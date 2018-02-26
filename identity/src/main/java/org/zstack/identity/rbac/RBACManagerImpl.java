package org.zstack.identity.rbac;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.header.AbstractService;
import org.zstack.header.Component;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.identity.InternalPolicy;
import org.zstack.header.identity.PolicyInventory;
import org.zstack.header.identity.role.*;
import org.zstack.header.identity.role.api.*;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.regex.Pattern;

public class RBACManagerImpl extends AbstractService implements RBACManager, Component {
    private static final CLogger logger = Utils.getLogger(RBACManagerImpl.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleApiMessage(msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    static {
        BeanUtils.reflections.getSubTypesOf(InternalPolicy.class).forEach(clz -> {
            try {
                InternalPolicy p = clz.newInstance();

                List<PolicyInventory> pis = p.getPolices();

                for (PolicyInventory pi : pis) {
                    for (PolicyInventory.Statement statement : pi.getStatements()) {
                        if (statement.getActions() != null) {
                            for (String s : statement.getActions()) {
                                try {
                                    Pattern.compile(s);
                                } catch (Exception e) {
                                    throw new CloudRuntimeException(String.format("invalid action[%s] defined by %s, it's not a regular expression string",
                                            s, clz), e);
                                }
                            }
                        }

                        if (statement.getResources() != null) {
                            for (String r : statement.getResources()) {
                                try {
                                    Pattern.compile(r);
                                } catch (Exception e) {
                                    throw new CloudRuntimeException(String.format("invalid resource[%s] defined by %s, it's not a regular expression string",
                                            r, clz), e);
                                }
                            }
                        }
                    }
                }

                internalPolices.addAll(pis);
            } catch (CloudRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CloudRuntimeException(e);
            }
        });

        internalAllowStatements.putAll(RBACManager.collectAllowedStatements(internalPolices));
        internalDenyStatements.putAll(RBACManager.collectDenyStatements(internalPolices));
    }

    private void handleLocalMessage(Message msg) {
        bus.dealWithUnknownMessage(msg);
    }

    private void handleApiMessage(Message msg) {
        if (msg instanceof APICreateRoleMsg) {
            handle((APICreateRoleMsg) msg);
        } else if (msg instanceof APIDeleteRoleMsg) {
            handle((APIDeleteRoleMsg) msg);
        } else if (msg instanceof APIAttachRoleToUserMsg) {
            handle((APIAttachRoleToUserMsg) msg);
        } else if (msg instanceof APIDetachRoleFromUserMsg) {
            handle((APIDetachRoleFromUserMsg) msg);
        } else if (msg instanceof APIAttachRoleToUserGroupMsg) {
            handle((APIAttachRoleToUserGroupMsg) msg);
        } else if (msg instanceof APIDetachRoleFromUserGroupMsg) {
            handle((APIDetachRoleFromUserGroupMsg) msg);
        } else if (msg instanceof APIAttachRoleToAccountMsg) {
            handle((APIAttachRoleToAccountMsg) msg);
        } else if (msg instanceof APIDetachRoleFromAccountMsg) {
            handle((APIDetachRoleFromAccountMsg) msg);
        } else if (msg instanceof APIAttachPolicyToRoleMsg) {
            handle((APIAttachPolicyToRoleMsg) msg);
        } else if (msg instanceof APIDetachPolicyFromRoleMsg) {
            handle((APIDetachPolicyFromRoleMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(APIDetachPolicyFromRoleMsg msg) {
        SQL.New(RolePolicyRefVO.class).eq(RolePolicyRefVO_.policyUuid, msg.getPolicyUuid())
                .eq(RolePolicyRefVO_.roleUuid, msg.getRoleUuid()).hardDelete();
        bus.publish(new APIDetachPolicyFromRoleEvent(msg.getId()));
    }

    private void handle(APIAttachPolicyToRoleMsg msg) {
        RolePolicyRefVO ref = new RolePolicyRefVO();
        ref.setPolicyUuid(msg.getPolicyUuid());
        ref.setRoleUuid(msg.getRoleUuid());
        dbf.persist(ref);
        bus.publish(new APIAttachPolicyToRoleEvent(msg.getId()));
    }

    private void handle(APIDetachRoleFromAccountMsg msg) {
        SQL.New(RoleAccountRefVO.class).eq(RoleAccountRefVO_.accountUuid, msg.getAccountUuid())
                .eq(RoleAccountRefVO_.roleUuid, msg.getRoleUuid()).hardDelete();

        APIDetachRoleFromAccountEvent evt = new APIDetachRoleFromAccountEvent(msg.getId());
        bus.publish(evt);
    }

    private void handle(APIAttachRoleToAccountMsg msg) {
        RoleAccountRefVO ref = new RoleAccountRefVO();
        ref.setAccountUuid(msg.getAccountUuid());
        ref.setRoleUuid(msg.getRoleUuid());
        dbf.persist(ref);
        bus.publish(new APIAttachRoleToAccountEvent(msg.getId()));
    }

    private void handle(APIDetachRoleFromUserGroupMsg msg) {
        SQL.New(RoleUserGroupRefVO.class).eq(RoleUserGroupRefVO_.groupUuid, msg.getGroupUuid())
                .eq(RoleUserGroupRefVO_.roleUuid, msg.getRoleUuid()).hardDelete();
        bus.publish(new APIDetachRoleFromUserGroupEvent(msg.getId()));
    }

    private void handle(APIAttachRoleToUserGroupMsg msg) {
        RoleUserGroupRefVO ref = new RoleUserGroupRefVO();
        ref.setRoleUuid(msg.getRoleUuid());
        ref.setGroupUuid(msg.getGroupUuid());
        dbf.persist(ref);
        bus.publish(new APIAttachRoleToUserGroupEvent(msg.getId()));
    }

    private void handle(APIDetachRoleFromUserMsg msg) {
        SQL.New(RoleUserRefVO.class).eq(RoleUserRefVO_.roleUuid, msg.getRoleUuid())
                .eq(RoleUserRefVO_.userUuid, msg.getUserUuid()).hardDelete();
        bus.publish(new APIDetachRoleFromUserEvent(msg.getId()));
    }

    private void handle(APIAttachRoleToUserMsg msg) {
        RoleUserRefVO vo = new RoleUserRefVO();
        vo.setUserUuid(msg.getUserUuid());
        vo.setRoleUuid(msg.getRoleUuid());
        dbf.persist(vo);
        bus.publish(new APIAttachRoleToUserEvent(msg.getId()));
    }

    private void handle(APIDeleteRoleMsg msg) {
        SQL.New(RoleVO.class).eq(RoleVO_.uuid, msg.getUuid()).hardDelete();
        bus.publish(new APIDeleteRoleEvent(msg.getId()));
    }

    private void handle(APICreateRoleMsg msg) {
        RoleVO vo = new RoleVO();
        vo.setUuid(msg.getResourceUuid() == null ? Platform.getUuid() : msg.getResourceUuid());
        vo.setName(msg.getName());
        vo.setDescription(msg.getDescription());
        vo = dbf.persistAndRefresh(vo);

        APICreateRoleEvent evt = new APICreateRoleEvent(msg.getId());
        evt.setInventory(RoleInventory.valueOf(vo));
        bus.publish(evt);
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(SERVICE_ID);
    }
}

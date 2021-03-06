/*******************************************************************************
 * Copyright (c) 2015 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipsescout.demo.bahbah.server.services.process;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.exception.VetoException;
import org.eclipse.scout.rt.platform.holders.IntegerHolder;
import org.eclipse.scout.rt.platform.holders.NVPair;
import org.eclipse.scout.rt.server.clientnotification.ClientNotificationRegistry;
import org.eclipse.scout.rt.server.jdbc.SQL;
import org.eclipse.scout.rt.server.services.common.clustersync.IClusterSynchronizationService;
import org.eclipse.scout.rt.shared.TEXTS;
import org.eclipse.scout.rt.shared.services.common.code.ICode;
import org.eclipse.scout.rt.shared.services.common.security.ACCESS;
import org.eclipsescout.demo.bahbah.server.ServerSession;
import org.eclipsescout.demo.bahbah.server.services.notification.RegisterUserNotification;
import org.eclipsescout.demo.bahbah.server.services.notification.UnregisterUserNotification;
import org.eclipsescout.demo.bahbah.server.util.UserUtility;
import org.eclipsescout.demo.bahbah.shared.notification.RefreshBuddiesNotification;
import org.eclipsescout.demo.bahbah.shared.security.CreateUserPermission;
import org.eclipsescout.demo.bahbah.shared.security.DeleteUserPermission;
import org.eclipsescout.demo.bahbah.shared.security.ReadUsersPermission;
import org.eclipsescout.demo.bahbah.shared.security.RegisterUserPermission;
import org.eclipsescout.demo.bahbah.shared.security.UnregisterUserPermission;
import org.eclipsescout.demo.bahbah.shared.security.UpdateUserPermission;
import org.eclipsescout.demo.bahbah.shared.services.UserAdministrationTablePageData;
import org.eclipsescout.demo.bahbah.shared.services.code.UserRoleCodeType;
import org.eclipsescout.demo.bahbah.shared.services.process.IUserProcessService;
import org.eclipsescout.demo.bahbah.shared.services.process.UserFormData;

public class UserProcessService implements IUserProcessService {
  private final Set<String> m_users = Collections.synchronizedSet(new HashSet<String>());

  @Override
  public void registerUser() {
    if (!ACCESS.check(new RegisterUserPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }

    registerUserInternal(ServerSession.get().getUserId());
    BEANS.get(IClusterSynchronizationService.class).publishTransactional(new RegisterUserNotification(ServerSession.get().getUserId()));
  }

  @Override
  public void unregisterUser() {
    if (!ACCESS.check(new UnregisterUserPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }

    m_users.remove(ServerSession.get().getUserId());

    BEANS.get(ClientNotificationRegistry.class).putForAllSessions(new RefreshBuddiesNotification());
    BEANS.get(IClusterSynchronizationService.class).publishTransactional(new UnregisterUserNotification(ServerSession.get().getUserId()));

  }

  @Override
  public void createUser(UserFormData formData) {
    if (!ACCESS.check(new CreateUserPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }

    UserUtility.createNewUser(formData.getUsername().getValue(), formData.getPassword().getValue().toCharArray(), formData.getUserRole().getValue());
  }

  @Override
  public void deleteUsers(List<Long> u_id) {
    if (!ACCESS.check(new DeleteUserPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }

    // check that we are not deleting ourselves
    IntegerHolder holder = new IntegerHolder();
    SQL.selectInto("SELECT u_id FROM TABUSERS WHERE username = :username INTO :myId", new NVPair("myId", holder), new NVPair("username", ServerSession.get().getUserId()));
    Long myId = Long.valueOf(holder.getValue());
    for (Long uid : u_id) {
      if (uid.equals(myId)) {
        throw new VetoException(TEXTS.get("CannotDeleteYourself"));
      }
    }

    SQL.delete("DELETE FROM TABUSERS WHERE u_id = :ids", new NVPair("ids", u_id));

    //TODO [7.0] jbr: what to do if the deleted user is still logged in somewhere?
  }

  @Override
  public void updateUser(UserFormData formData) {
    if (!ACCESS.check(new UpdateUserPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }
    UserUtility.checkUsername(formData.getUsername().getValue());
    UserUtility.checkPermissionId(formData.getUserRole().getValue());

    SQL.update("UPDATE TABUSERS SET username = :newUsername, permission_id = :newPermId WHERE u_id = :uid",
        new NVPair("newUsername", formData.getUsername().getValue()), new NVPair("newPermId", formData.getUserRole().getValue()), new NVPair("uid", formData.getUserId()));
  }

  @Override
  public UserAdministrationTablePageData getUserAdministrationTableData(UserFormData formData) {
    if (!ACCESS.check(new ReadUsersPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }
    UserAdministrationTablePageData pageData = new UserAdministrationTablePageData();
    SQL.selectInto("SELECT u_id, username, permission_id FROM TABUSERS INTO :userId, :username, :role", pageData);
    return pageData;
  }

  @Override
  public Set<String> getUsersOnline() {
    if (!ACCESS.check(new ReadUsersPermission())) {
      throw new VetoException(TEXTS.get("AuthorizationFailed"));
    }

    return Collections.unmodifiableSet(m_users);
  }

  @Override
  public ICode<Integer> getUserPermission() {
    IntegerHolder ih = new IntegerHolder(0);
    SQL.selectInto("SELECT permission_id FROM TABUSERS WHERE username = :username INTO :permission", new NVPair("username", ServerSession.get().getUserId()), new NVPair("permission", ih));

    return BEANS.get(UserRoleCodeType.class).getCode(ih.getValue());
  }

  @Override
  public void registerUserInternal(String userId) {
    m_users.add(userId);
    BEANS.get(ClientNotificationRegistry.class).putForAllSessions(new RefreshBuddiesNotification());
  }

  @Override
  public void unregisterUserInternal(String userName) {
    m_users.remove(ServerSession.get().getUserId());
    BEANS.get(ClientNotificationRegistry.class).putForAllSessions(new RefreshBuddiesNotification());
  }
}

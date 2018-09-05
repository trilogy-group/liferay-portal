/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.service.impl;

import com.liferay.exportimport.kernel.lar.ExportImportThreadLocal;
import com.liferay.exportimport.kernel.staging.MergeLayoutPrototypesThreadLocal;
import com.liferay.portal.kernel.bean.BeanPropertiesUtil;
import com.liferay.portal.kernel.dao.orm.QueryPos;
import com.liferay.portal.kernel.dao.orm.SQLQuery;
import com.liferay.portal.kernel.dao.orm.Session;
import com.liferay.portal.kernel.dao.orm.Type;
import com.liferay.portal.kernel.exception.NoSuchResourceActionException;
import com.liferay.portal.kernel.exception.NoSuchResourcePermissionException;
import com.liferay.portal.kernel.exception.NoSuchRoleException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.AuditedModel;
import com.liferay.portal.kernel.model.GroupedModel;
import com.liferay.portal.kernel.model.Resource;
import com.liferay.portal.kernel.model.ResourceAction;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.ResourcePermission;
import com.liferay.portal.kernel.model.ResourcePermissionConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.RoleConstants;
import com.liferay.portal.kernel.search.IndexWriterHelperUtil;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionUpdateHandler;
import com.liferay.portal.kernel.security.permission.PermissionUpdateHandlerRegistryUtil;
import com.liferay.portal.kernel.security.permission.ResourceActionsUtil;
import com.liferay.portal.kernel.service.ExceptionRetryAcceptor;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.permission.ModelPermissions;
import com.liferay.portal.kernel.service.permission.ModelPermissionsFactory;
import com.liferay.portal.kernel.spring.aop.Property;
import com.liferay.portal.kernel.spring.aop.Retry;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.model.impl.ResourceImpl;
import com.liferay.portal.security.permission.PermissionCacheUtil;
import com.liferay.portal.service.base.ResourcePermissionLocalServiceBaseImpl;
import com.liferay.portal.util.PropsValues;
import com.liferay.portal.util.ResourcePermissionsThreadLocal;
import com.liferay.util.dao.orm.CustomSQLUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the local service for accessing, adding, checking, deleting,
 * granting, and revoking resource permissions.
 *
 * <p>
 * Before attempting to read any of the documentation for this class, first read
 * {@link com.liferay.portal.model.impl.ResourcePermissionImpl} for an
 * explanation of scoping.
 * </p>
 *
 * @author Brian Wing Shun Chan
 * @author Raymond Augé
 * @author Connor McKay
 */
public class ResourcePermissionLocalServiceImpl
	extends ResourcePermissionLocalServiceBaseImpl {

	/**
	 * @see com.liferay.portal.verify.VerifyPermission#fixOrganizationRolePermissions
	 */
	public static final String[] EMPTY_ACTION_IDS = {null};

	@Override
	public void addModelResourcePermissions(
			AuditedModel auditedModel, ServiceContext serviceContext)
		throws PortalException {

		ModelPermissions modelPermissions =
			serviceContext.getModelPermissions();

		if ((modelPermissions != null) && !modelPermissions.isEmpty()) {
			addModelResourcePermissions(
				auditedModel.getCompanyId(), getGroupId(auditedModel),
				auditedModel.getUserId(), auditedModel.getModelClassName(),
				String.valueOf(auditedModel.getPrimaryKeyObj()),
				modelPermissions);
		}
		else if (serviceContext.isAddGroupPermissions() ||
				 serviceContext.isAddGuestPermissions()) {

			addResourcePermissions(
				auditedModel.getCompanyId(), getGroupId(auditedModel),
				auditedModel.getUserId(), auditedModel.getModelClassName(),
				String.valueOf(auditedModel.getPrimaryKeyObj()), false,
				serviceContext.isAddGroupPermissions(),
				serviceContext.isAddGuestPermissions());
		}
		else {
			if (serviceContext.isDeriveDefaultPermissions()) {
				serviceContext.deriveDefaultPermissions(
					getGroupId(auditedModel), auditedModel.getModelClassName());
			}

			addModelResourcePermissions(
				auditedModel.getCompanyId(), getGroupId(auditedModel),
				auditedModel.getUserId(), auditedModel.getModelClassName(),
				String.valueOf(auditedModel.getPrimaryKeyObj()),
				serviceContext.getModelPermissions());
		}
	}

	@Override
	public void addModelResourcePermissions(
			long companyId, long groupId, long userId, String name,
			String primKey, ModelPermissions modelPermissions)
		throws PortalException {

		if (!PermissionThreadLocal.isAddResource()) {
			return;
		}

		validate(name, false);

		if (primKey == null) {
			return;
		}

		// Individual Permissions

		boolean flushResourcePermissionEnabled =
			PermissionThreadLocal.isFlushResourcePermissionEnabled(
				name, primKey);

		PermissionThreadLocal.setFlushResourcePermissionEnabled(
			name, primKey, false);

		try {

			// Owner permissions

			Role ownerRole = roleLocalService.getRole(
				companyId, RoleConstants.OWNER);

			List<String> ownerActionIds =
				ResourceActionsUtil.getModelResourceActions(name);

			filterOwnerActions(name, ownerActionIds);

			String[] ownerPermissions = ownerActionIds.toArray(
				new String[ownerActionIds.size()]);

			setOwnerResourcePermissions(
				companyId, name, ResourceConstants.SCOPE_INDIVIDUAL, primKey,
				ownerRole.getRoleId(), userId, ownerPermissions);

			if (modelPermissions != null) {
				for (String roleName : modelPermissions.getRoleNames()) {
					Role role = getRole(companyId, groupId, roleName);

					setResourcePermissions(
						companyId, name, ResourceConstants.SCOPE_INDIVIDUAL,
						primKey, role.getRoleId(),
						modelPermissions.getActionIds(roleName));
				}
			}
		}
		finally {
			PermissionThreadLocal.setFlushResourcePermissionEnabled(
				name, primKey, flushResourcePermissionEnabled);

			PermissionCacheUtil.clearResourcePermissionCache(
				ResourceConstants.SCOPE_INDIVIDUAL, name, primKey);

			IndexWriterHelperUtil.updatePermissionFields(name, primKey);
		}
	}

	/**
	 * Adds resources for the model with the name and primary key string, always
	 * creating a resource at the individual scope and only creating resources
	 * at the group, group template, and company scope if such resources don't
	 * already exist.
	 *
	 * @param companyId the primary key of the portal instance
	 * @param groupId the primary key of the group
	 * @param userId the primary key of the user adding the resources
	 * @param name a name for the resource, typically the model's class name
	 * @param primKey the primary key string of the model instance, optionally
	 *        an empty string if no instance exists
	 * @param groupPermissions the group permissions to be applied
	 * @param guestPermissions the guest permissions to be applied
	 */
	@Override
	public void addModelResourcePermissions(
			long companyId, long groupId, long userId, String name,
			String primKey, String[] groupPermissions,
			String[] guestPermissions)
		throws PortalException {

		ModelPermissions modelPermissions = ModelPermissionsFactory.create(
			groupPermissions, guestPermissions);

		addModelResourcePermissions(
			companyId, groupId, userId, name, primKey, modelPermissions);
	}

	/**
	 * Grants the role permission at the scope to perform the action on
	 * resources of the type. Existing actions are retained.
	 *
	 * <p>
	 * This method cannot be used to grant individual scope permissions, but is
	 * only intended for adding permissions at the company, group, and
	 * group-template scopes. For example, this method could be used to grant a
	 * company scope permission to edit message board posts.
	 * </p>
	 *
	 * <p>
	 * If a company scope permission is granted to resources that the role
	 * already had group scope permissions to, the group scope permissions are
	 * deleted. Likewise, if a group scope permission is granted to resources
	 * that the role already had company scope permissions to, the company scope
	 * permissions are deleted. Be aware that this latter behavior can result in
	 * an overall reduction in permissions for the role.
	 * </p>
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope. This method only supports company, group, and
	 *        group-template scope.
	 * @param primKey the primary key
	 * @param roleId the primary key of the role
	 * @param actionId the action ID
	 */
	@Override
	@Retry(
		acceptor = ExceptionRetryAcceptor.class,
		properties = {
			@Property(
				name = ExceptionRetryAcceptor.EXCEPTION_NAME,
				value = "org.springframework.dao.DataIntegrityViolationException"
			)
		}
	)
	public void addResourcePermission(
			long companyId, String name, int scope, String primKey, long roleId,
			String actionId)
		throws PortalException {

		if (scope == ResourceConstants.SCOPE_COMPANY) {

			// Remove group permission

			removeResourcePermissions(
				companyId, name, ResourceConstants.SCOPE_GROUP, roleId,
				actionId);
		}
		else if (scope == ResourceConstants.SCOPE_GROUP) {

			// Remove company permission

			removeResourcePermissions(
				companyId, name, ResourceConstants.SCOPE_COMPANY, roleId,
				actionId);
		}
		else if (scope == ResourceConstants.SCOPE_INDIVIDUAL) {
			throw new NoSuchResourcePermissionException();
		}

		updateResourcePermission(
			companyId, name, scope, primKey, roleId, 0, new String[] {actionId},
			ResourcePermissionConstants.OPERATOR_ADD);
	}

	/**
	 * Adds resources for the entity with the name and primary key string,
	 * always creating a resource at the individual scope and only creating
	 * resources at the group, group template, and company scope if such
	 * resources don't already exist.
	 *
	 * @param companyId the primary key of the portal instance
	 * @param groupId the primary key of the group
	 * @param userId the primary key of the user adding the resources
	 * @param name a name for the resource, which should be a portlet ID if the
	 *        resource is a portlet or the resource's class name otherwise
	 * @param primKey the primary key string of the resource instance,
	 *        optionally an empty string if no instance exists
	 * @param portletActions whether to associate portlet actions with the
	 *        resource
	 * @param addGroupPermissions whether to add group permissions
	 * @param addGuestPermissions whether to add guest permissions
	 */
	@Override
	public void addResourcePermissions(
			long companyId, long groupId, long userId, String name,
			String primKey, boolean portletActions, boolean addGroupPermissions,
			boolean addGuestPermissions)
		throws PortalException {

		if (!PermissionThreadLocal.isAddResource()) {
			return;
		}

		validate(name, portletActions);

		if (primKey == null) {
			return;
		}

		// Individual

		Resource resource = new ResourceImpl();

		resource.setCompanyId(companyId);
		resource.setName(name);
		resource.setScope(ResourceConstants.SCOPE_INDIVIDUAL);
		resource.setPrimKey(primKey);

		// Permissions

		boolean flushResourcePermissionEnabled =
			PermissionThreadLocal.isFlushResourcePermissionEnabled(
				name, primKey);

		PermissionThreadLocal.setFlushResourcePermissionEnabled(
			name, primKey, false);

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S_P(
				companyId, name, ResourceConstants.SCOPE_INDIVIDUAL, primKey);

		ResourcePermissionsThreadLocal.setResourcePermissions(
			resourcePermissions);

		try {
			List<String> actionIds = null;

			if (portletActions) {
				actionIds = ResourceActionsUtil.getPortletResourceActions(
					resource.getName());
			}
			else {
				actionIds = ResourceActionsUtil.getModelResourceActions(
					resource.getName());

				filterOwnerActions(resource.getName(), actionIds);
			}

			Role role = roleLocalService.getRole(
				companyId, RoleConstants.OWNER);

			setOwnerResourcePermissions(
				resource.getCompanyId(), resource.getName(),
				resource.getScope(), resource.getPrimKey(), role.getRoleId(),
				userId, actionIds.toArray(new String[actionIds.size()]));

			// Group permissions

			if ((groupId > 0) && addGroupPermissions) {
				List<String> actions = null;

				if (portletActions) {
					actions =
						ResourceActionsUtil.
							getPortletResourceGroupDefaultActions(name);
				}
				else {
					actions =
						ResourceActionsUtil.getModelResourceGroupDefaultActions(
							name);
				}

				addGroupPermissions(
					groupId, resource,
					actions.toArray(new String[actions.size()]));
			}

			// Guest permissions

			if (addGuestPermissions) {

				// Don't add guest permissions when you've already added group
				// permissions and the given group is the guest group.

				List<String> actions = null;

				if (portletActions) {
					actions =
						ResourceActionsUtil.
							getPortletResourceGuestDefaultActions(
								resource.getName());
				}
				else {
					actions =
						ResourceActionsUtil.getModelResourceGuestDefaultActions(
							resource.getName());
				}

				addGuestPermissions(
					resource, actions.toArray(new String[actions.size()]));
			}
		}
		finally {
			ResourcePermissionsThreadLocal.setResourcePermissions(null);

			PermissionThreadLocal.setFlushResourcePermissionEnabled(
				name, primKey, flushResourcePermissionEnabled);

			PermissionCacheUtil.clearResourcePermissionCache(
				ResourceConstants.SCOPE_INDIVIDUAL, name, primKey);

			IndexWriterHelperUtil.updatePermissionFields(name, primKey);
		}
	}

	/**
	 * Grants the role permissions at the scope to perform the actions on all
	 * resources of the type. Existing actions are retained.
	 *
	 * <p>
	 * This method should only be used to add default permissions to existing
	 * resources en masse during upgrades or while verifying permissions. For
	 * example, this method could be used to grant site members individual scope
	 * permissions to view all blog posts.
	 * </p>
	 *
	 * @param resourceName the resource's name, which can be either a class name
	 *        or a portlet ID
	 * @param roleName the role's name
	 * @param scope the scope
	 * @param resourceActionBitwiseValue the bitwise IDs of the actions
	 */
	@Override
	public void addResourcePermissions(
		String resourceName, String roleName, int scope,
		long resourceActionBitwiseValue) {

		List<Role> roles = rolePersistence.findByName(roleName);

		if (roles.isEmpty()) {
			return;
		}

		Session session = resourcePermissionPersistence.openSession();

		try {

			// Update existing resource permissions

			String sql = CustomSQLUtil.get(_UPDATE_ACTION_IDS);

			sql = StringUtil.replace(
				sql, "[$ROLE_ID$]",
				ListUtil.toString(roles, Role.ROLE_ID_ACCESSOR));

			SQLQuery sqlQuery = session.createSynchronizedSQLQuery(sql);

			QueryPos qPos = QueryPos.getInstance(sqlQuery);

			qPos.add(resourceActionBitwiseValue);
			qPos.add(resourceActionBitwiseValue);
			qPos.add(resourceName);
			qPos.add(scope);

			sqlQuery.executeUpdate();

			// Add missing resource permissions

			sql = CustomSQLUtil.get(_FIND_MISSING_RESOURCE_PERMISSIONS);

			sqlQuery = session.createSynchronizedSQLQuery(sql);

			sqlQuery.addScalar("companyId", Type.LONG);
			sqlQuery.addScalar("name", Type.STRING);
			sqlQuery.addScalar("scope", Type.INTEGER);
			sqlQuery.addScalar("primKey", Type.STRING);
			sqlQuery.addScalar("roleId", Type.LONG);

			qPos = QueryPos.getInstance(sqlQuery);

			qPos.add(resourceName);
			qPos.add(scope);
			qPos.add(roleName);

			List<Object[]> resourcePermissionArrays = sqlQuery.list(true);

			if (resourcePermissionArrays.isEmpty()) {
				return;
			}

			for (Object[] resourcePermissionArray : resourcePermissionArrays) {
				long resourcePermissionId = counterLocalService.increment(
					ResourcePermission.class.getName());

				ResourcePermission resourcePermission =
					resourcePermissionPersistence.create(resourcePermissionId);

				resourcePermission.setCompanyId(
					(Long)resourcePermissionArray[0]);
				resourcePermission.setName((String)resourcePermissionArray[1]);
				resourcePermission.setScope(
					(Integer)resourcePermissionArray[2]);

				String primKey = (String)resourcePermissionArray[3];

				resourcePermission.setPrimKey(primKey);
				resourcePermission.setPrimKeyId(GetterUtil.getLong(primKey));

				resourcePermission.setRoleId((Long)resourcePermissionArray[4]);
				resourcePermission.setActionIds(resourceActionBitwiseValue);
				resourcePermission.setViewActionId(
					resourceActionBitwiseValue % 2 == 1);

				session.save(resourcePermission);

				PermissionCacheUtil.clearResourcePermissionCache(
					resourcePermission.getScope(), resourcePermission.getName(),
					resourcePermission.getPrimKey());
			}
		}
		catch (Exception e) {
			throw new SystemException(e);
		}
		finally {
			resourcePermissionPersistence.closeSession(session);

			resourcePermissionPersistence.clearCache();
		}
	}

	/**
	 * Deletes all resource permissions at the scope to resources of the type.
	 * This method should not be confused with any of the
	 * <code>removeResourcePermission</code> methods, as its purpose is very
	 * different. This method should only be used for deleting resource
	 * permissions that refer to a resource when that resource is deleted. For
	 * example this method could be used to delete all individual scope
	 * permissions to a blog post when it is deleted.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 */
	@Override
	public void deleteResourcePermissions(
			long companyId, String name, int scope, long primKey)
		throws PortalException {

		deleteResourcePermissions(
			companyId, name, scope, String.valueOf(primKey));
	}

	/**
	 * Deletes all resource permissions at the scope to resources of the type.
	 * This method should not be confused with any of the
	 * <code>removeResourcePermission</code> methods, as its purpose is very
	 * different. This method should only be used for deleting resource
	 * permissions that refer to a resource when that resource is deleted. For
	 * example this method could be used to delete all individual scope
	 * permissions to a blog post when it is deleted.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 */
	@Override
	public void deleteResourcePermissions(
			long companyId, String name, int scope, String primKey)
		throws PortalException {

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S_P(
				companyId, name, scope, primKey);

		for (ResourcePermission resourcePermission : resourcePermissions) {
			deleteResourcePermission(
				resourcePermission.getResourcePermissionId());
		}
	}

	@Override
	public ResourcePermission fetchResourcePermission(
		long companyId, String name, int scope, String primKey, long roleId) {

		return resourcePermissionPersistence.fetchByC_N_S_P_R(
			companyId, name, scope, primKey, roleId);
	}

	@Override
	public Map<Long, Set<String>> getAvailableResourcePermissionActionIds(
		long companyId, String name, int scope, String primKey,
		Collection<String> actionIds) {

		if (actionIds.isEmpty()) {
			return Collections.emptyMap();
		}

		List<ResourcePermission> resourcePermissions = getResourcePermissions(
			companyId, name, scope, primKey);

		Map<Long, Set<String>> roleIdsToActionIds = new HashMap<>(
			resourcePermissions.size());

		for (ResourcePermission resourcePermission : resourcePermissions) {
			if (resourcePermission.getActionIds() == 0) {
				roleIdsToActionIds.put(
					resourcePermission.getRoleId(),
					Collections.<String>emptySet());

				continue;
			}

			Set<String> availableActionIds = new HashSet<>(actionIds.size());

			for (String actionId : actionIds) {
				if (resourcePermission.hasActionId(actionId)) {
					availableActionIds.add(actionId);
				}
			}

			if (!availableActionIds.isEmpty()) {
				roleIdsToActionIds.put(
					resourcePermission.getRoleId(), availableActionIds);
			}
		}

		return roleIdsToActionIds;
	}

	/**
	 * Returns the intersection of action IDs the role has permission at the
	 * scope to perform on resources of the type.
	 *
	 * @param  companyId he primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @param  roleId the primary key of the role
	 * @param  actionIds the action IDs
	 * @return the intersection of action IDs the role has permission at the
	 *         scope to perform on resources of the type
	 */
	@Override
	public List<String> getAvailableResourcePermissionActionIds(
			long companyId, String name, int scope, String primKey, long roleId,
			Collection<String> actionIds)
		throws PortalException {

		ResourcePermission resourcePermission =
			resourcePermissionPersistence.fetchByC_N_S_P_R(
				companyId, name, scope, primKey, roleId);

		if (resourcePermission == null) {
			return Collections.emptyList();
		}

		List<String> availableActionIds = new ArrayList<>(actionIds.size());

		for (String actionId : actionIds) {
			ResourceAction resourceAction =
				resourceActionLocalService.getResourceAction(name, actionId);

			if (resourcePermission.hasAction(resourceAction)) {
				availableActionIds.add(actionId);
			}
		}

		return availableActionIds;
	}

	/**
	 * @deprecated As of Wilberforce (7.0.x), replaced by {@link
	 *             #getAvailableResourcePermissionActionIds(long, String, int,
	 *             String, Collection)}
	 */
	@Deprecated
	@Override
	public Map<Long, Set<String>> getAvailableResourcePermissionActionIds(
		long companyId, String name, int scope, String primKey, long[] roleIds,
		Collection<String> actionIds) {

		return getAvailableResourcePermissionActionIds(
			companyId, name, scope, primKey, new ArrayList<String>(actionIds));
	}

	/**
	 * Returns the resource permission for the role at the scope to perform the
	 * actions on resources of the type.
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @param  roleId the primary key of the role
	 * @return the resource permission for the role at the scope to perform the
	 *         actions on resources of the type
	 */
	@Override
	public ResourcePermission getResourcePermission(
			long companyId, String name, int scope, String primKey, long roleId)
		throws PortalException {

		return resourcePermissionPersistence.findByC_N_S_P_R(
			companyId, name, scope, primKey, roleId);
	}

	/**
	 * Returns all the resource permissions at the scope of the type.
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @return the resource permissions at the scope of the type
	 */
	@Override
	public List<ResourcePermission> getResourcePermissions(
		long companyId, String name, int scope, String primKey) {

		return resourcePermissionPersistence.findByC_N_S_P(
			companyId, name, scope, primKey);
	}

	/**
	 * Returns the number of resource permissions at the scope of the type.
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @return the number of resource permissions at the scope of the type
	 */
	@Override
	public int getResourcePermissionsCount(
		long companyId, String name, int scope, String primKey) {

		return resourcePermissionPersistence.countByC_N_S_P(
			companyId, name, scope, primKey);
	}

	/**
	 * Returns the resource permissions that apply to the resource.
	 *
	 * @param  companyId the primary key of the resource's company
	 * @param  groupId the primary key of the resource's group
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  primKey the primary key of the resource
	 * @return the resource permissions associated with the resource
	 */
	@Override
	public List<ResourcePermission> getResourceResourcePermissions(
		long companyId, long groupId, String name, String primKey) {

		return resourcePermissionFinder.findByResource(
			companyId, groupId, name, primKey);
	}

	/**
	 * Returns all the resource permissions for the role.
	 *
	 * @param  roleId the primary key of the role
	 * @return the resource permissions for the role
	 */
	@Override
	public List<ResourcePermission> getRoleResourcePermissions(long roleId) {
		return resourcePermissionPersistence.findByRoleId(roleId);
	}

	/**
	 * Returns a range of all the resource permissions for the role at the
	 * scopes.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end -
	 * start</code> instances. <code>start</code> and <code>end</code> are not
	 * primary keys, they are indexes in the result set. Thus, <code>0</code>
	 * refers to the first result in the set. Setting both <code>start</code>
	 * and <code>end</code> to {@link
	 * com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS} will return the full
	 * result set.
	 * </p>
	 *
	 * @param  roleId the primary key of the role
	 * @param  scopes the scopes
	 * @param  start the lower bound of the range of results
	 * @param  end the upper bound of the range of results (not inclusive)
	 * @return the range of resource permissions for the role at the scopes
	 */
	@Override
	public List<ResourcePermission> getRoleResourcePermissions(
		long roleId, int[] scopes, int start, int end) {

		return resourcePermissionFinder.findByR_S(roleId, scopes, start, end);
	}

	@Override
	public List<Role> getRoles(
			long companyId, String name, int scope, String primKey,
			String actionId)
		throws PortalException {

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S_P(
				companyId, name, scope, primKey);

		if (resourcePermissions.isEmpty()) {
			return Collections.emptyList();
		}

		ResourceAction resourceAction =
			resourceActionLocalService.getResourceAction(name, actionId);

		Set<Long> rolesIds = new HashSet<>();

		for (ResourcePermission resourcePermission : resourcePermissions) {
			if (resourcePermission.hasAction(resourceAction)) {
				rolesIds.add(resourcePermission.getRoleId());
			}
		}

		List<Role> roles = new ArrayList<>(rolesIds.size());

		for (long roleId : rolesIds) {
			roles.add(roleLocalService.getRole(roleId));
		}

		return roles;
	}

	/**
	 * Returns all the resource permissions where scope = any &#63;.
	 *
	 * <p>
	 * Useful when paginating results. Returns a maximum of <code>end -
	 * start</code> instances. <code>start</code> and <code>end</code> are not
	 * primary keys, they are indexes in the result set. Thus, <code>0</code>
	 * refers to the first result in the set. Setting both <code>start</code>
	 * and <code>end</code> to {@link
	 * com.liferay.portal.kernel.dao.orm.QueryUtil#ALL_POS} will return the full
	 * result set.
	 * </p>
	 *
	 * @param  scopes the scopes
	 * @return the resource permissions where scope = any &#63;
	 */
	@Override
	public List<ResourcePermission> getScopeResourcePermissions(int[] scopes) {
		return resourcePermissionPersistence.findByScope(scopes);
	}

	/**
	 * Returns <code>true</code> if the resource permission grants permission to
	 * perform the resource action. Note that this method does not ensure that
	 * the resource permission refers to the same type of resource as the
	 * resource action.
	 *
	 * @param  resourcePermission the resource permission
	 * @param  resourceAction the resource action
	 * @return <code>true</code> if the resource permission grants permission to
	 *         perform the resource action
	 */
	@Override
	public boolean hasActionId(
		ResourcePermission resourcePermission, ResourceAction resourceAction) {

		return resourcePermission.hasAction(resourceAction);
	}

	/**
	 * Returns <code>true</code> if the roles have permission at the scope to
	 * perform the action on the resources.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param  resources the resources
	 * @param  roleIds the primary keys of the roles
	 * @param  actionId the action ID
	 * @return <code>true</code> if any one of the roles has permission to
	 *         perform the action on any one of the resources;
	 *         <code>false</code> otherwise
	 */
	@Override
	public boolean hasResourcePermission(
			List<Resource> resources, long[] roleIds, String actionId)
		throws PortalException {

		if (roleIds.length == 0) {
			return false;
		}

		int size = resources.size();

		if (size < 2) {
			throw new IllegalArgumentException(
				"The list of resources must contain at least two values");
		}

		Resource individualResource = resources.get(0);

		if (individualResource.getScope() !=
				ResourceConstants.SCOPE_INDIVIDUAL) {

			throw new IllegalArgumentException(
				"The first resource must be an individual scope");
		}

		Resource companyResource = resources.get(size - 1);

		if (companyResource.getScope() != ResourceConstants.SCOPE_COMPANY) {
			throw new IllegalArgumentException(
				"The last resource must be a company scope");
		}

		// See LPS-47464

		if (resourcePermissionPersistence.countByC_N_S_P(
				individualResource.getCompanyId(), individualResource.getName(),
				individualResource.getScope(),
				individualResource.getPrimKey()) < 1) {

			StringBundler sb = new StringBundler(9);

			sb.append("{companyId=");
			sb.append(individualResource.getCompanyId());
			sb.append(", name=");
			sb.append(individualResource.getName());
			sb.append(", primKey=");
			sb.append(individualResource.getPrimKey());
			sb.append(", scope=");
			sb.append(individualResource.getScope());
			sb.append("}");

			throw new NoSuchResourcePermissionException(sb.toString());
		}

		// Iterate the list of resources in reverse order to test permissions
		// from company scope to individual scope because it is more likely that
		// a permission is assigned at a higher scope. Optimizing this method to
		// one SQL call may actually slow things down since most of the calls
		// will pull from the cache after the first request.

		for (int i = size - 1; i >= 0; i--) {
			Resource resource = resources.get(i);

			if (hasResourcePermission(
					resource.getCompanyId(), resource.getName(),
					resource.getScope(), resource.getPrimKey(), roleIds,
					actionId)) {

				return true;
			}
		}

		return false;
	}

	/**
	 * Returns <code>true</code> if the role has permission at the scope to
	 * perform the action on resources of the type.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @param  roleId the primary key of the role
	 * @param  actionId the action ID
	 * @return <code>true</code> if the role has permission to perform the
	 *         action on the resource; <code>false</code> otherwise
	 */
	@Override
	public boolean hasResourcePermission(
			long companyId, String name, int scope, String primKey, long roleId,
			String actionId)
		throws PortalException {

		ResourcePermission resourcePermission =
			resourcePermissionPersistence.fetchByC_N_S_P_R(
				companyId, name, scope, primKey, roleId);

		if (resourcePermission == null) {
			return false;
		}

		ResourceAction resourceAction =
			resourceActionLocalService.getResourceAction(name, actionId);

		if (resourcePermission.hasAction(resourceAction)) {
			return true;
		}

		return false;
	}

	/**
	 * Returns <code>true</code> if the roles have permission at the scope to
	 * perform the action on resources of the type.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  primKey the primary key
	 * @param  roleIds the primary keys of the roles
	 * @param  actionId the action ID
	 * @return <code>true</code> if any one of the roles has permission to
	 *         perform the action on the resource; <code>false</code> otherwise
	 */
	@Override
	public boolean hasResourcePermission(
			long companyId, String name, int scope, String primKey,
			long[] roleIds, String actionId)
		throws PortalException {

		if (roleIds.length == 0) {
			return false;
		}

		ResourceAction resourceAction =
			resourceActionLocalService.getResourceAction(name, actionId);

		if (roleIds.length >
				PropsValues.
					PERMISSIONS_ROLE_RESOURCE_PERMISSION_QUERY_THRESHOLD) {

			int count = resourcePermissionFinder.countByC_N_S_P_R_A(
				companyId, name, scope, primKey, roleIds,
				resourceAction.getBitwiseValue());

			if (count > 0) {
				return true;
			}
		}
		else {
			List<ResourcePermission> resourcePermissions =
				resourcePermissionPersistence.findByC_N_S_P_R(
					companyId, name, scope, primKey, roleIds);

			if (resourcePermissions.isEmpty()) {
				return false;
			}

			for (ResourcePermission resourcePermission : resourcePermissions) {
				if (resourcePermission.hasAction(resourceAction)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * @deprecated As of Wilberforce (7.0.x), replaced by {@link #getRoles(long,
	 *             String, int, String, String}
	 */
	@Deprecated
	@Override
	public boolean[] hasResourcePermissions(
			long companyId, String name, int scope, String primKey,
			long[] roleIds, String actionId)
		throws PortalException {

		boolean[] hasResourcePermissions = new boolean[roleIds.length];

		if (roleIds.length == 0) {
			return hasResourcePermissions;
		}

		ResourceAction resourceAction =
			resourceActionLocalService.getResourceAction(name, actionId);

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S_P_R(
				companyId, name, scope, primKey, roleIds);

		if (resourcePermissions.isEmpty()) {
			return hasResourcePermissions;
		}

		for (ResourcePermission resourcePermission : resourcePermissions) {
			if (resourcePermission.hasAction(resourceAction)) {
				long roleId = resourcePermission.getRoleId();

				for (int i = 0; i < roleIds.length; i++) {
					if (roleIds[i] == roleId) {
						hasResourcePermissions[i] = true;

						break;
					}
				}
			}
		}

		return hasResourcePermissions;
	}

	/**
	 * Returns <code>true</code> if the role has permission at the scope to
	 * perform the action on the resource.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param  companyId the primary key of the company
	 * @param  name the resource's name, which can be either a class name or a
	 *         portlet ID
	 * @param  scope the scope
	 * @param  roleId the primary key of the role
	 * @param  actionId the action ID
	 * @return <code>true</code> if the role has permission to perform the
	 *         action on the resource; <code>false</code> otherwise
	 */
	@Override
	public boolean hasScopeResourcePermission(
			long companyId, String name, int scope, long roleId,
			String actionId)
		throws PortalException {

		ResourceAction resourceAction =
			resourceActionLocalService.getResourceAction(name, actionId);

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S(companyId, name, scope);

		for (ResourcePermission resourcePermission : resourcePermissions) {
			if (resourcePermission.hasAction(resourceAction)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Reassigns all the resource permissions from the source role to the
	 * destination role, and deletes the source role.
	 *
	 * @param fromRoleId the primary key of the source role
	 * @param toRoleId the primary key of the destination role
	 */
	@Override
	public void mergePermissions(long fromRoleId, long toRoleId)
		throws PortalException {

		Role fromRole = rolePersistence.findByPrimaryKey(fromRoleId);
		Role toRole = rolePersistence.findByPrimaryKey(toRoleId);

		if (fromRole.getType() != toRole.getType()) {
			throw new PortalException("Role types are mismatched");
		}
		else if (toRole.isSystem()) {
			throw new PortalException("Cannot move permissions to system role");
		}
		else if (fromRole.isSystem()) {
			throw new PortalException(
				"Cannot move permissions from system role");
		}

		List<ResourcePermission> resourcePermissions =
			getRoleResourcePermissions(fromRoleId);

		for (ResourcePermission resourcePermission : resourcePermissions) {
			resourcePermission.setRoleId(toRoleId);

			resourcePermissionPersistence.update(resourcePermission);
		}

		roleLocalService.deleteRole(fromRoleId);
	}

	/**
	 * Grants the role default permissions to all the resources of the type and
	 * at the scope stored in the resource permission, deletes the resource
	 * permission, and deletes the resource permission's role if it has no
	 * permissions remaining.
	 *
	 * @param resourcePermissionId the primary key of the resource permission
	 * @param toRoleId the primary key of the role
	 */
	@Override
	public void reassignPermissions(long resourcePermissionId, long toRoleId)
		throws PortalException {

		ResourcePermission resourcePermission = getResourcePermission(
			resourcePermissionId);

		long companyId = resourcePermission.getCompanyId();
		String name = resourcePermission.getName();
		int scope = resourcePermission.getScope();
		String primKey = resourcePermission.getPrimKey();
		long fromRoleId = resourcePermission.getRoleId();

		Role toRole = roleLocalService.getRole(toRoleId);

		List<String> actionIds = null;

		if (toRole.getType() == RoleConstants.TYPE_REGULAR) {
			actionIds = ResourceActionsUtil.getModelResourceActions(name);
		}
		else {
			actionIds = ResourceActionsUtil.getModelResourceGroupDefaultActions(
				name);
		}

		setResourcePermissions(
			companyId, name, scope, primKey, toRoleId,
			actionIds.toArray(new String[actionIds.size()]));

		resourcePermissionPersistence.remove(resourcePermissionId);

		List<ResourcePermission> resourcePermissions =
			getRoleResourcePermissions(fromRoleId);

		if (resourcePermissions.isEmpty()) {
			roleLocalService.deleteRole(fromRoleId);
		}
	}

	/**
	 * Revokes permission at the scope from the role to perform the action on
	 * resources of the type. For example, this method could be used to revoke a
	 * group scope permission to edit blog posts.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param roleId the primary key of the role
	 * @param actionId the action ID
	 */
	@Override
	public void removeResourcePermission(
			long companyId, String name, int scope, String primKey, long roleId,
			String actionId)
		throws PortalException {

		updateResourcePermission(
			companyId, name, scope, primKey, roleId, 0, new String[] {actionId},
			ResourcePermissionConstants.OPERATOR_REMOVE);
	}

	/**
	 * Revokes all permissions at the scope from the role to perform the action
	 * on resources of the type. For example, this method could be used to
	 * revoke all individual scope permissions to edit blog posts from site
	 * members.
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param roleId the primary key of the role
	 * @param actionId the action ID
	 */
	@Override
	public void removeResourcePermissions(
			long companyId, String name, int scope, long roleId,
			String actionId)
		throws PortalException {

		List<ResourcePermission> resourcePermissions =
			resourcePermissionPersistence.findByC_N_S(companyId, name, scope);

		for (ResourcePermission resourcePermission : resourcePermissions) {
			updateResourcePermission(
				companyId, name, scope, resourcePermission.getPrimKey(), roleId,
				0, new String[] {actionId},
				ResourcePermissionConstants.OPERATOR_REMOVE);
		}
	}

	/**
	 * Updates the role's permissions at the scope, setting the actions that can
	 * be performed on resources of the type, also setting the owner of any
	 * newly created resource permissions. Existing actions are replaced.
	 *
	 * <p>
	 * This method can be used to set permissions at any scope, but it is
	 * generally only used at the individual scope. For example, it could be
	 * used to set the guest permissions on a blog post.
	 * </p>
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param roleId the primary key of the role
	 * @param ownerId the primary key of the owner (generally the user that
	 *        created the resource)
	 * @param actionIds the action IDs of the actions
	 */
	@Override
	@Retry(
		acceptor = ExceptionRetryAcceptor.class,
		properties = {
			@Property(
				name = ExceptionRetryAcceptor.EXCEPTION_NAME,
				value = "org.springframework.dao.DataIntegrityViolationException"
			)
		}
	)
	public void setOwnerResourcePermissions(
			long companyId, String name, int scope, String primKey, long roleId,
			long ownerId, String[] actionIds)
		throws PortalException {

		updateResourcePermission(
			companyId, name, scope, primKey, roleId, ownerId, actionIds,
			ResourcePermissionConstants.OPERATOR_SET);
	}

	/**
	 * Updates the role's permissions at the scope, setting the actions that can
	 * be performed on resources of the type. Existing actions are replaced.
	 *
	 * <p>
	 * This method can be used to set permissions at any scope, but it is
	 * generally only used at the individual scope. For example, it could be
	 * used to set the guest permissions on a blog post.
	 * </p>
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param roleId the primary key of the role
	 * @param actionIds the action IDs of the actions
	 */
	@Override
	@Retry(
		acceptor = ExceptionRetryAcceptor.class,
		properties = {
			@Property(
				name = ExceptionRetryAcceptor.EXCEPTION_NAME,
				value = "org.springframework.dao.DataIntegrityViolationException"
			)
		}
	)
	public void setResourcePermissions(
			long companyId, String name, int scope, String primKey, long roleId,
			String[] actionIds)
		throws PortalException {

		updateResourcePermission(
			companyId, name, scope, primKey, roleId, 0, actionIds,
			ResourcePermissionConstants.OPERATOR_SET);
	}

	/**
	 * Updates the role's permissions at the scope, setting the actions that can
	 * be performed on resources of the type. Existing actions are replaced.
	 *
	 * <p>
	 * This method can be used to set permissions at any scope, but it is
	 * generally only used at the individual scope. For example, it could be
	 * used to set the guest permissions on a blog post.
	 * </p>
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param roleIdsToActionIds a map of role IDs to action IDs of the actions
	 */
	@Override
	@Retry(
		acceptor = ExceptionRetryAcceptor.class,
		properties = {
			@Property(
				name = ExceptionRetryAcceptor.EXCEPTION_NAME,
				value = "org.springframework.dao.DataIntegrityViolationException"
			)
		}
	)
	public void setResourcePermissions(
			long companyId, String name, int scope, String primKey,
			Map<Long, String[]> roleIdsToActionIds)
		throws PortalException {

		updateResourcePermission(
			companyId, name, scope, primKey, 0, roleIdsToActionIds);
	}

	/**
	 * Updates the resources for the model, replacing their group and guest
	 * permissions with new ones from the service context.
	 *
	 * @param auditedModel the model associated with the resources
	 * @param serviceContext the service context to be applied. Can set group
	 *        and guest permissions.
	 */
	@Override
	public void updateModelResourcePermissions(
			AuditedModel auditedModel, ServiceContext serviceContext)
		throws PortalException {

		updateResourcePermissions(
			auditedModel.getCompanyId(), getGroupId(auditedModel),
			auditedModel.getModelClassName(),
			String.valueOf(auditedModel.getPrimaryKeyObj()),
			serviceContext.getModelPermissions());
	}

	/**
	 * Updates resources matching the group, name, and primary key at the
	 * individual scope, setting new group and guest permissions.
	 *
	 * @param companyId the primary key of the portal instance
	 * @param groupId the primary key of the group
	 * @param name the resource's name, which should be a portlet ID if the
	 *        resource is a portlet or the resource's class name otherwise
	 * @param primKey the primary key of the resource instance
	 * @param groupPermissions the group permissions to be applied
	 * @param guestPermissions the guest permissions to be applied
	 */
	@Override
	public void updateResourcePermissions(
			long companyId, long groupId, String name, long primKey,
			String[] groupPermissions, String[] guestPermissions)
		throws PortalException {

		Resource resource = new ResourceImpl();

		resource.setCompanyId(companyId);
		resource.setName(name);
		resource.setScope(ResourceConstants.SCOPE_INDIVIDUAL);
		resource.setPrimKey(String.valueOf(primKey));

		if (groupPermissions == null) {
			groupPermissions = new String[0];
		}

		if (guestPermissions == null) {
			guestPermissions = new String[0];
		}

		addGroupPermissions(groupId, resource, groupPermissions);

		addGuestPermissions(resource, guestPermissions);
	}

	@Override
	public void updateResourcePermissions(
			long companyId, long groupId, String name, String primKey,
			ModelPermissions modelPermissions)
		throws PortalException {

		for (String roleName : modelPermissions.getRoleNames()) {
			Role role = getRole(companyId, groupId, roleName);

			List<String> actionIds = modelPermissions.getActionIdsList(
				roleName);

			setResourcePermissions(
				companyId, name, ResourceConstants.SCOPE_INDIVIDUAL, primKey,
				role.getRoleId(),
				actionIds.toArray(new String[actionIds.size()]));
		}
	}

	@Override
	public void updateResourcePermissions(
			long companyId, long groupId, String name, String primKey,
			String[] groupPermissions, String[] guestPermissions)
		throws PortalException {

		ModelPermissions modelPermissions = ModelPermissionsFactory.create(
			groupPermissions, guestPermissions);

		updateResourcePermissions(
			companyId, groupId, name, primKey, modelPermissions);
	}

	@Override
	public void updateResourcePermissions(
		long companyId, String name, int scope, String primKey,
		String newPrimKey) {

		List<ResourcePermission> resourcePermissions = getResourcePermissions(
			companyId, name, scope, primKey);

		for (ResourcePermission resourcePermission : resourcePermissions) {
			resourcePermission.setPrimKey(newPrimKey);
			resourcePermission.setPrimKeyId(GetterUtil.getLong(newPrimKey));

			resourcePermissionPersistence.update(resourcePermission);
		}
	}

	protected void addGroupPermissions(
			long groupId, Resource resource, String[] actionIds)
		throws PortalException {

		Role role = roleLocalService.getDefaultGroupRole(groupId);

		setResourcePermissions(
			resource.getCompanyId(), resource.getName(), resource.getScope(),
			resource.getPrimKey(), role.getRoleId(), actionIds);
	}

	protected void addGuestPermissions(Resource resource, String[] actionIds)
		throws PortalException {

		Role guestRole = roleLocalService.getRole(
			resource.getCompanyId(), RoleConstants.GUEST);

		setResourcePermissions(
			resource.getCompanyId(), resource.getName(), resource.getScope(),
			resource.getPrimKey(), guestRole.getRoleId(), actionIds);
	}

	private void _updateResourcePermission(
			long companyId, String name, int scope, String primKey,
			long ownerId, long roleId, String[] actionIds, int operator,
			boolean fetch)
		throws PortalException {

		ResourcePermission resourcePermission = null;

		Map<Long, ResourcePermission> resourcePermissionsMap =
			ResourcePermissionsThreadLocal.getResourcePermissions();

		if (resourcePermissionsMap != null) {
			resourcePermission = resourcePermissionsMap.get(roleId);
		}
		else if (fetch) {
			resourcePermission = resourcePermissionPersistence.fetchByC_N_S_P_R(
				companyId, name, scope, primKey, roleId);
		}

		if (resourcePermission == null) {
			if ((operator == ResourcePermissionConstants.OPERATOR_ADD) &&
				(actionIds.length == 0)) {

				return;
			}

			if (operator == ResourcePermissionConstants.OPERATOR_REMOVE) {
				return;
			}

			long resourcePermissionId = counterLocalService.increment(
				ResourcePermission.class.getName());

			resourcePermission = resourcePermissionPersistence.create(
				resourcePermissionId);

			resourcePermission.setCompanyId(companyId);
			resourcePermission.setName(name);
			resourcePermission.setScope(scope);
			resourcePermission.setPrimKey(primKey);
			resourcePermission.setPrimKeyId(GetterUtil.getLong(primKey));
			resourcePermission.setRoleId(roleId);
			resourcePermission.setOwnerId(ownerId);

			if (resourcePermissionsMap != null) {
				resourcePermissionsMap.put(roleId, resourcePermission);
			}
		}

		List<String> unsupportedActionIds = Collections.emptyList();

		if (((operator == ResourcePermissionConstants.OPERATOR_ADD) ||
			 (operator == ResourcePermissionConstants.OPERATOR_SET)) &&
			isGuestRoleId(companyId, roleId)) {

			unsupportedActionIds =
				ResourceActionsUtil.getResourceGuestUnsupportedActions(
					name, name);
		}

		long actionIdsLong = resourcePermission.getActionIds();

		if (operator == ResourcePermissionConstants.OPERATOR_SET) {
			actionIdsLong = 0;
		}

		for (String actionId : actionIds) {
			if (actionId == null) {
				break;
			}

			if (unsupportedActionIds.contains(actionId)) {
				throw new PrincipalException(
					actionId + "is not supported by role " + roleId);
			}

			ResourceAction resourceAction =
				resourceActionLocalService.getResourceAction(name, actionId);

			if ((operator == ResourcePermissionConstants.OPERATOR_ADD) ||
				(operator == ResourcePermissionConstants.OPERATOR_SET)) {

				actionIdsLong |= resourceAction.getBitwiseValue();
			}
			else {
				actionIdsLong =
					actionIdsLong & (~resourceAction.getBitwiseValue());
			}
		}

		if ((actionIdsLong != resourcePermission.getActionIds()) ||
			resourcePermission.isNew()) {

			resourcePermission.setActionIds(actionIdsLong);
			resourcePermission.setViewActionId(actionIdsLong % 2 == 1);

			resourcePermissionPersistence.update(resourcePermission);

			if (ArrayUtil.contains(actionIds, ActionKeys.MANAGE_SUBGROUPS)) {
				PermissionCacheUtil.clearPrimaryKeyRoleCache();
			}

			IndexWriterHelperUtil.updatePermissionFields(name, primKey);
		}
	}

	protected void filterOwnerActions(String name, List<String> actionIds) {
		List<String> defaultOwnerActions =
			ResourceActionsUtil.getModelResourceOwnerDefaultActions(name);

		if (!defaultOwnerActions.isEmpty()) {
			actionIds.retainAll(defaultOwnerActions);
		}
	}

	protected long getGroupId(AuditedModel auditedModel) {
		long groupId = 0;

		if (auditedModel instanceof GroupedModel) {
			GroupedModel groupedModel = (GroupedModel)auditedModel;

			groupId = BeanPropertiesUtil.getLongSilent(
				groupedModel, "resourceGroupId", groupedModel.getGroupId());
		}

		return groupId;
	}

	protected Role getRole(long companyId, long groupId, String roleName)
		throws PortalException {

		if (roleName.equals(RoleConstants.PLACEHOLDER_DEFAULT_GROUP_ROLE)) {
			if (groupId == 0) {
				throw new NoSuchRoleException(
					"Specify a group ID other than 0 for role name " +
						RoleConstants.PLACEHOLDER_DEFAULT_GROUP_ROLE);
			}

			return roleLocalService.getDefaultGroupRole(groupId);
		}

		return roleLocalService.getRole(companyId, roleName);
	}

	protected boolean isGuestRoleId(long companyId, long roleId)
		throws PortalException {

		Role guestRole = roleLocalService.getRole(
			companyId, RoleConstants.GUEST);

		if (roleId == guestRole.getRoleId()) {
			return true;
		}

		return false;
	}

	/**
	 * Updates the role's permissions at the scope, either adding to, removing
	 * from, or setting the actions that can be performed on resources of the
	 * type. Automatically creates a new resource permission if none exists, or
	 * deletes the existing resource permission if it no longer grants
	 * permissions to perform any action.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param roleId the primary key of the role
	 * @param ownerId the primary key of the owner
	 * @param actionIds the action IDs of the actions
	 * @param operator whether to add to, remove from, or set/replace the
	 *        existing actions. Possible values can be found in {@link
	 *        ResourcePermissionConstants}.
	 */
	protected void updateResourcePermission(
			long companyId, String name, int scope, String primKey, long roleId,
			long ownerId, String[] actionIds, int operator)
		throws PortalException {

		_updateResourcePermission(
			companyId, name, scope, primKey, ownerId, roleId, actionIds,
			operator, true);
	}

	/**
	 * Updates the role's permissions at the scope, either adding to, removing
	 * from, or setting the actions that can be performed on resources of the
	 * type. Automatically creates a new resource permission if none exists, or
	 * deletes the existing resource permission if it no longer grants
	 * permissions to perform any action.
	 *
	 * <p>
	 * Depending on the scope, the value of <code>primKey</code> will have
	 * different meanings. For more information, see {@link
	 * com.liferay.portal.model.impl.ResourcePermissionImpl}.
	 * </p>
	 *
	 * @param companyId the primary key of the company
	 * @param name the resource's name, which can be either a class name or a
	 *        portlet ID
	 * @param scope the scope
	 * @param primKey the primary key
	 * @param ownerId the primary key of the owner
	 */
	protected void updateResourcePermission(
			long companyId, String name, int scope, String primKey,
			long ownerId, Map<Long, String[]> roleIdsToActionIds)
		throws PortalException {

		boolean flushResourcePermissionEnabled =
			PermissionThreadLocal.isFlushResourcePermissionEnabled(
				name, primKey);

		PermissionThreadLocal.setFlushResourcePermissionEnabled(
			name, primKey, false);

		try {
			long[] roleIds = ArrayUtil.toLongArray(roleIdsToActionIds.keySet());

			List<ResourcePermission> resourcePermissions = new ArrayList<>(
				roleIds.length);

			int batchSize = 1000;
			int start = 0;

			while (start < (roleIds.length - batchSize)) {
				resourcePermissions.addAll(
					resourcePermissionPersistence.findByC_N_S_P_R(
						companyId, name, scope, primKey,
						ArrayUtil.subset(roleIds, start, start + batchSize)));

				start += batchSize;
			}

			resourcePermissions.addAll(
				resourcePermissionPersistence.findByC_N_S_P_R(
					companyId, name, scope, primKey,
					ArrayUtil.subset(roleIds, start, roleIds.length)));

			roleIdsToActionIds = new HashMap<>(roleIdsToActionIds);

			for (ResourcePermission resourcePermission : resourcePermissions) {
				long roleId = resourcePermission.getRoleId();

				String[] actionIds = roleIdsToActionIds.remove(roleId);

				_updateResourcePermission(
					companyId, name, scope, primKey, ownerId, roleId, actionIds,
					ResourcePermissionConstants.OPERATOR_SET, true);
			}

			if (roleIdsToActionIds.isEmpty()) {
				return;
			}

			for (Map.Entry<Long, String[]> entry :
					roleIdsToActionIds.entrySet()) {

				long roleId = entry.getKey();
				String[] actionIds = entry.getValue();

				_updateResourcePermission(
					companyId, name, scope, primKey, ownerId, roleId, actionIds,
					ResourcePermissionConstants.OPERATOR_SET, false);
			}

			if (!MergeLayoutPrototypesThreadLocal.isInProgress() &&
				!ExportImportThreadLocal.isImportInProcess()) {

				PermissionUpdateHandler permissionUpdateHandler =
					PermissionUpdateHandlerRegistryUtil.
						getPermissionUpdateHandler(name);

				if (permissionUpdateHandler != null) {
					permissionUpdateHandler.updatedPermission(primKey);
				}
			}
		}
		finally {
			PermissionThreadLocal.setFlushResourcePermissionEnabled(
				name, primKey, flushResourcePermissionEnabled);

			PermissionCacheUtil.clearResourcePermissionCache(
				scope, name, primKey);

			IndexWriterHelperUtil.updatePermissionFields(name, primKey);
		}
	}

	protected void validate(String name, boolean portletActions)
		throws PortalException {

		List<String> actions = null;

		if (portletActions) {
			actions = ResourceActionsUtil.getPortletResourceActions(name);
		}
		else {
			actions = ResourceActionsUtil.getModelResourceActions(name);
		}

		if (ListUtil.isEmpty(actions)) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Checking other resource actions because no model or " +
						"portlet resource actions found for " + name);
			}

			int count = resourceActionPersistence.countByName(name);

			if (count == 0) {
				throw new NoSuchResourceActionException(
					"There are no actions associated with the resource " +
						name);
			}
		}
	}

	private static final String _FIND_MISSING_RESOURCE_PERMISSIONS =
		ResourcePermissionLocalServiceImpl.class.getName() +
			".findMissingResourcePermissions";

	private static final String _UPDATE_ACTION_IDS =
		ResourcePermissionLocalServiceImpl.class.getName() + ".updateActionIds";

	private static final Log _log = LogFactoryUtil.getLog(
		ResourcePermissionLocalServiceImpl.class);

}
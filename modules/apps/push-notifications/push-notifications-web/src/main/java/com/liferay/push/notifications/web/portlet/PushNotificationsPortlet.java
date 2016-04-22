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

package com.liferay.push.notifications.web.portlet;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.push.notifications.constants.PushNotificationsPortletKeys;
import com.liferay.push.notifications.service.PushNotificationsDeviceLocalServiceUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;

import org.osgi.service.component.annotations.Component;

/**
 * @author Bruno Farache
 */
@Component(
	immediate = true,
	property = {
		"com.liferay.portlet.css-class-wrapper=push-notifications",
		"com.liferay.portlet.display-category=category.hidden",
		"javax.portlet.display-name=Push Notifications",
		"javax.portlet.expiration-cache=0",
		"javax.portlet.info.keywords=Push Notifications",
		"javax.portlet.info.short-title=Push Notifications",
		"javax.portlet.info.title=Push Notifications",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + PushNotificationsPortletKeys.PUSH_NOTIFICATIONS,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=administrator",
		"javax.portlet.supports.mime-type=text/html"
	},
	service = Portlet.class
)
public class PushNotificationsPortlet extends MVCPortlet {

	public void deletePushNotificationsDevice(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		long pushNotificationsDeviceId = ParamUtil.getLong(
			actionRequest, "pushNotificationsDeviceId");

		try {
			PushNotificationsDeviceLocalServiceUtil.
				deletePushNotificationsDevice(pushNotificationsDeviceId);

			SessionMessages.add(
				actionRequest, "pushNotificationsDeviceDeleted");
		}
		catch (PortalException pe) {
			SessionErrors.add(actionRequest, pe.getClass());
		}

		sendRedirect(actionRequest, actionResponse);
	}

}
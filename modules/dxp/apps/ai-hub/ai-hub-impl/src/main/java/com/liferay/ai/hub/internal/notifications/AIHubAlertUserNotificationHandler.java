/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.notifications;

import com.liferay.ai.hub.internal.constants.AIHubPortletKeys;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserNotificationEvent;
import com.liferay.portal.kernel.notifications.BaseUserNotificationHandler;
import com.liferay.portal.kernel.notifications.UserNotificationHandler;
import com.liferay.portal.kernel.service.ServiceContext;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	property = "jakarta.portlet.name=" + AIHubPortletKeys.AI_HUB_ALERT_NOTIFICATION,
	service = UserNotificationHandler.class
)
public class AIHubAlertUserNotificationHandler
	extends BaseUserNotificationHandler {

	public AIHubAlertUserNotificationHandler() {
		setPortletId(AIHubPortletKeys.AI_HUB_ALERT_NOTIFICATION);
	}

	@Override
	public boolean hasPermission(long classPK, User user) {
		return true;
	}

	@Override
	public boolean isDeliver(
		long userId, long classNameId, int notificationType, int deliveryType,
		ServiceContext serviceContext) {

		return true;
	}

	@Override
	protected String getBody(
			UserNotificationEvent userNotificationEvent,
			ServiceContext serviceContext)
		throws Exception {

		JSONObject jsonObject = _jsonFactory.createJSONObject(
			userNotificationEvent.getPayload());

		return _language.format(
			serviceContext.getLocale(),
			"agent-x-triggered-5-x-guardrail-alerts-in-the-last-5-minutes",
			new Object[] {
				jsonObject.getString("agentExternalReferenceCode"),
				jsonObject.getString("guardrailType")
			},
			false);
	}

	@Override
	protected String getLink(
			UserNotificationEvent userNotificationEvent,
			ServiceContext serviceContext)
		throws Exception {

		return "/group/control_panel/manage?p_p_id=" +
			"com_liferay_portal_security_audit_web_portlet_AuditPortlet";
	}

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

}
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.constants;

/**
 * @author Danny Situ
 */
public class NotificationConstants {

	public static final String
		NOTIFICATION_TEMPLATE_EMAIL_EXTERNAL_REFERENCE_CODE =
			"L_AI_HUB_ALERT_EMAIL_TEMPLATE";

	public static final String
		NOTIFICATION_TEMPLATE_USER_NOTIFICATION_EXTERNAL_REFERENCE_CODE =
			"L_AI_HUB_ALERT_USER_NOTIFICATION_TEMPLATE";

	public static final String NOTIFICATION_TYPE_EMAIL = "email";

	public static final String NOTIFICATION_TYPE_USER_NOTIFICATION =
		"userNotification";

	public static final String SEVERITY_CRITICAL = "critical";

	public static String getNotificationTemplateExternalReferenceCode(
		String notificationType) {

		if (NOTIFICATION_TYPE_EMAIL.equals(notificationType)) {
			return NOTIFICATION_TEMPLATE_EMAIL_EXTERNAL_REFERENCE_CODE;
		}

		if (NOTIFICATION_TYPE_USER_NOTIFICATION.equals(notificationType)) {
			return NOTIFICATION_TEMPLATE_USER_NOTIFICATION_EXTERNAL_REFERENCE_CODE;
		}

		return null;
	}

}
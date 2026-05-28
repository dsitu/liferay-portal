/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.notification;

import com.liferay.ai.hub.internal.constants.AIHubDestinationNames;
import com.liferay.ai.hub.notification.AIHubAlertEventBuffer;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.security.audit.AuditMessageProcessor;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	property = "eventTypes=AI_HUB_GUARDRAIL_ALERT",
	service = AuditMessageProcessor.class
)
public class AIGuardrailAlertAuditMessageProcessor
	implements AuditMessageProcessor {

	@Override
	public void process(AuditMessage auditMessage) {
		try {
			if (!_aiHubAlertEventBuffer.shouldDispatch(
					auditMessage.getEventType())) {

				return;
			}

			Message message = new Message();

			message.setPayload(
				auditMessage.toJSONObject(
				).toString());

			_messageBus.sendMessage(
				AIHubDestinationNames.AI_HUB_ALERT_ROUTING, message);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to dispatch AI guardrail alert: " + auditMessage,
				exception);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		AIGuardrailAlertAuditMessageProcessor.class);

	@Reference
	private AIHubAlertEventBuffer _aiHubAlertEventBuffer;

	@Reference
	private MessageBus _messageBus;

}
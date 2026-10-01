/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.portlet.action;

import com.liferay.commerce.constants.CommercePortletKeys;
import com.liferay.commerce.digital.signature.internal.util.DSCommerceOrderAttachmentUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.service.CommerceOrderAttachmentLocalService;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCResourceCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.ResourceRequest;
import jakarta.portlet.ResourceResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(
	property = {
		"jakarta.portlet.name=" + CommercePortletKeys.COMMERCE_ORDER,
		"mvc.command.name=/commerce_order/add_ds_request"
	},
	service = MVCResourceCommand.class
)
public class AddDSRequestMVCResourceCommand extends BaseMVCResourceCommand {

	@Override
	protected void doServeResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		CommerceOrderAttachment commerceOrderAttachment =
			_commerceOrderAttachmentLocalService.getCommerceOrderAttachment(
				ParamUtil.getLong(
					resourceRequest, "commerceOrderAttachmentId"));

		CommerceOrder commerceOrder =
			_commerceOrderLocalService.getCommerceOrder(
				commerceOrderAttachment.getCommerceOrderId());

		_commerceOrderModelResourcePermission.check(
			themeDisplay.getPermissionChecker(), commerceOrder,
			ActionKeys.UPDATE);

		_commerceOrderAttachmentModelResourcePermission.check(
			themeDisplay.getPermissionChecker(), commerceOrderAttachment,
			ActionKeys.UPDATE);

		if (!DSCommerceOrderAttachmentUtil.isEnabled(commerceOrder)) {
			throw new PortalException(
				"Digital signature is not enabled for order " +
					commerceOrder.getCommerceOrderId());
		}

		int expireAfterDays = ParamUtil.getInteger(
			resourceRequest, "expireAfter");
		int expireWarnDays = ParamUtil.getInteger(
			resourceRequest, "expireWarn");

		if ((expireAfterDays > 0) && (expireWarnDays >= expireAfterDays)) {
			throw new PortalException(
				_language.get(
					themeDisplay.getLocale(),
					"days-to-warn-signers-must-be-fewer-than-days-until-" +
						"expiration"));
		}

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			commerceOrder.getCompanyId(),
			commerceOrderAttachment.getFileEntryId());

		if ((dsRequest != null) && !dsRequest.isRequestable()) {
			throw new PortalException(
				StringBundler.concat(
					"Commerce order attachment ",
					commerceOrderAttachment.getCommerceOrderAttachmentId(),
					" already has an active or completed signature request"));
		}

		dsRequest = _dsRequestManager.addDSRequest(
			commerceOrder.getCompanyId(), commerceOrder.getGroupId(),
			themeDisplay.getUserId(),
			new DSEnvelope() {
				{
					dsRecipients = _getDSRecipients(
						commerceOrder, resourceRequest);
					emailBlurb = ParamUtil.getString(
						resourceRequest, "emailMessage");
					emailSubject = ParamUtil.getString(
						resourceRequest, "emailSubject");
					expireAfter = expireAfterDays;
					expireWarn = expireWarnDays;
					name = commerceOrderAttachment.getTitle();
				}
			},
			new long[] {commerceOrderAttachment.getFileEntryId()});

		_dsRequestManager.sendDSRequestNotifications(
			commerceOrder.getCompanyId(), commerceOrder.getGroupId(),
			dsRequest);

		JSONPortletResponseUtil.writeJSON(
			resourceRequest, resourceResponse, _jsonFactory.createJSONObject());
	}

	private List<DSRecipient> _getDSRecipients(
			CommerceOrder commerceOrder, ResourceRequest resourceRequest)
		throws Exception {

		List<DSRecipient> dsRecipients = new ArrayList<>();

		Set<Long> accountUserIds = new HashSet<>(
			TransformUtil.transform(
				DSCommerceOrderAttachmentUtil.getAccountUsers(commerceOrder),
				User::getUserId));

		int countersignerCount = 0;
		boolean sequential = ParamUtil.getBoolean(
			resourceRequest, "sequential", true);

		for (long recipientUserId :
				ParamUtil.getLongValues(resourceRequest, "recipientUserIds")) {

			User user = _userLocalService.getUser(recipientUserId);

			if ((user.getCompanyId() != commerceOrder.getCompanyId()) ||
				!user.isActive()) {

				throw new PortalException(
					StringBundler.concat(
						"User ", recipientUserId, " is not allowed to sign ",
						"order ", commerceOrder.getCommerceOrderId()));
			}

			if (!accountUserIds.contains(recipientUserId) &&
				(recipientUserId != commerceOrder.getUserId())) {

				countersignerCount++;
			}

			if (countersignerCount > 1) {
				throw new PortalException(
					"Only one internal countersigner is allowed");
			}

			int routingOrder = 1;

			if (sequential) {
				routingOrder = dsRecipients.size() + 1;
			}

			DSRecipient dsRecipient = new DSRecipient();

			dsRecipient.setDSRecipientId(
				String.valueOf(dsRecipients.size() + 1));
			dsRecipient.setEmailAddress(user.getEmailAddress());
			dsRecipient.setName(user.getFullName());
			dsRecipient.setRoutingOrder(routingOrder);

			dsRecipients.add(dsRecipient);
		}

		if (dsRecipients.isEmpty()) {
			throw new PortalException("At least one recipient is required");
		}

		return dsRecipients;
	}

	@Reference
	private CommerceOrderAttachmentLocalService
		_commerceOrderAttachmentLocalService;

	@Reference(
		target = "(model.class.name=com.liferay.commerce.model.CommerceOrderAttachment)"
	)
	private ModelResourcePermission<CommerceOrderAttachment>
		_commerceOrderAttachmentModelResourcePermission;

	@Reference
	private CommerceOrderLocalService _commerceOrderLocalService;

	@Reference(
		target = "(model.class.name=com.liferay.commerce.model.CommerceOrder)"
	)
	private ModelResourcePermission<CommerceOrder>
		_commerceOrderModelResourcePermission;

	@Reference
	private DSRequestManager _dsRequestManager;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private UserLocalService _userLocalService;

}
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
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCResourceCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.ResourceRequest;
import jakarta.portlet.ResourceResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(
	property = {
		"jakarta.portlet.name=" + CommercePortletKeys.COMMERCE_ORDER,
		"mvc.command.name=/commerce_order/void_ds_request"
	},
	service = MVCResourceCommand.class
)
public class VoidDSRequestMVCResourceCommand extends BaseMVCResourceCommand {

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

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			commerceOrder.getCompanyId(),
			commerceOrderAttachment.getFileEntryId());

		if (dsRequest != null) {
			_dsRequestManager.voidDSRequest(
				commerceOrder.getCompanyId(), commerceOrder.getGroupId(),
				dsRequest, "Voided by sender");
		}

		JSONPortletResponseUtil.writeJSON(
			resourceRequest, resourceResponse, _jsonFactory.createJSONObject());
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

}
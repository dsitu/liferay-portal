/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.frontend.data.set.action;

import com.liferay.commerce.constants.CommercePortletKeys;
import com.liferay.commerce.digital.signature.internal.util.DSCommerceOrderAttachmentUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderAttachmentAdminFDSActionContributor;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.frontend.data.set.model.FDSActionDropdownItem;
import com.liferay.frontend.data.set.model.FDSActionDropdownItemBuilder;
import com.liferay.frontend.data.set.model.FDSActionDropdownItemList;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.PortletURLFactory;
import com.liferay.portal.kernel.portlet.url.builder.ResourceURLBuilder;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.PortletRequest;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(service = CommerceOrderAttachmentAdminFDSActionContributor.class)
public class CommerceOrderAttachmentAdminFDSActionContributorImpl
	implements CommerceOrderAttachmentAdminFDSActionContributor {

	@Override
	public Map<String, Object> getAdditionalProps(
		CommerceOrder commerceOrder, HttpServletRequest httpServletRequest) {

		if (!DSCommerceOrderAttachmentUtil.isEnabled(commerceOrder)) {
			return Collections.emptyMap();
		}

		Map<Long, DSRequest> dsRequests =
			DSCommerceOrderAttachmentUtil.getDSRequests(
				commerceOrder, httpServletRequest);

		return HashMapBuilder.<String, Object>put(
			"signatureRequest",
			() -> {
				if (!_hasUpdatePermission(commerceOrder, httpServletRequest)) {
					return null;
				}

				return HashMapBuilder.<String, Object>put(
					"accountUsers",
					JSONUtil.toJSONArray(
						DSCommerceOrderAttachmentUtil.getAccountUsers(
							commerceOrder),
						DSCommerceOrderAttachmentUtil::getUserJSONObject)
				).put(
					"buyer",
					() -> {
						User user = _userLocalService.fetchUser(
							commerceOrder.getUserId());

						if ((user == null) || !user.isActive()) {
							return null;
						}

						return DSCommerceOrderAttachmentUtil.getUserJSONObject(
							user);
					}
				).put(
					"commerceOrderId", commerceOrder.getCommerceOrderId()
				).put(
					"nonrequestableIds", _getNonrequestableIds(dsRequests)
				).put(
					"portletNamespace",
					_portal.getPortletNamespace(
						CommercePortletKeys.COMMERCE_ORDER)
				).put(
					"searchUsersURL",
					ResourceURLBuilder.createResourceURL(
						_portletURLFactory.create(
							httpServletRequest,
							CommercePortletKeys.COMMERCE_ORDER,
							PortletRequest.RESOURCE_PHASE)
					).setResourceID(
						"/commerce_order/search_ds_request_users"
					).buildString()
				).build();
			}
		).put(
			"signatureStatuses",
			DSCommerceOrderAttachmentUtil.getSignatureStatuses(dsRequests)
		).build();
	}

	@Override
	public List<FDSActionDropdownItem> getFDSActionDropdownItems(
		CommerceOrder commerceOrder, HttpServletRequest httpServletRequest) {

		if (!DSCommerceOrderAttachmentUtil.isEnabled(commerceOrder)) {
			return Collections.emptyList();
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		return FDSActionDropdownItemList.of(
			FDSActionDropdownItemBuilder.putData(
				"addDSRequestURL",
				_getResourceURL(
					httpServletRequest, "/commerce_order/add_ds_request")
			).setHref(
				StringPool.POUND
			).setIcon(
				"signature"
			).setLabel(
				_language.get(httpServletRequest, "request-signature")
			).setPermissionKey(
				"update"
			).build(
				"request-signature"
			),
			FDSActionDropdownItemBuilder.putData(
				"signatureStatusURL",
				DSCommerceOrderAttachmentUtil.getActionURL(
					"/commerce-digital-signature/signature-status",
					themeDisplay)
			).setHref(
				StringPool.POUND
			).setIcon(
				"list-ul"
			).setLabel(
				_language.get(httpServletRequest, "view-signature-status")
			).build(
				"view-signature-status"
			));
	}

	private List<String> _getNonrequestableIds(
		Map<Long, DSRequest> dsRequests) {

		List<String> nonrequestableIds = new ArrayList<>();

		for (Map.Entry<Long, DSRequest> entry : dsRequests.entrySet()) {
			DSRequest dsRequest = entry.getValue();

			if (!dsRequest.isRequestable()) {
				nonrequestableIds.add(String.valueOf(entry.getKey()));
			}
		}

		return nonrequestableIds;
	}

	private String _getResourceURL(
		HttpServletRequest httpServletRequest, String resourceID) {

		return ResourceURLBuilder.createResourceURL(
			_portletURLFactory.create(
				httpServletRequest, CommercePortletKeys.COMMERCE_ORDER,
				PortletRequest.RESOURCE_PHASE)
		).setParameter(
			"commerceOrderAttachmentId", "{id}"
		).setResourceID(
			resourceID
		).buildString();
	}

	private boolean _hasUpdatePermission(
			CommerceOrder commerceOrder, HttpServletRequest httpServletRequest)
		throws PortalException {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		return _commerceOrderModelResourcePermission.contains(
			themeDisplay.getPermissionChecker(), commerceOrder,
			ActionKeys.UPDATE);
	}

	@Reference(
		target = "(model.class.name=com.liferay.commerce.model.CommerceOrder)"
	)
	private ModelResourcePermission<CommerceOrder>
		_commerceOrderModelResourcePermission;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

	@Reference
	private PortletURLFactory _portletURLFactory;

	@Reference
	private UserLocalService _userLocalService;

}
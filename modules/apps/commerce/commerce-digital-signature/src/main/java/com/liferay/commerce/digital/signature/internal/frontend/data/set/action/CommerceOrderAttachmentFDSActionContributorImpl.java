/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.frontend.data.set.action;

import com.liferay.commerce.digital.signature.internal.util.DSCommerceOrderAttachmentUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderAttachmentFDSActionContributor;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.frontend.data.set.model.FDSActionDropdownItem;
import com.liferay.frontend.data.set.model.FDSActionDropdownItemBuilder;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.WebKeys;

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
@Component(service = CommerceOrderAttachmentFDSActionContributor.class)
public class CommerceOrderAttachmentFDSActionContributorImpl
	implements CommerceOrderAttachmentFDSActionContributor {

	@Override
	public Map<String, Object> getAdditionalProps(
		CommerceOrder commerceOrder, HttpServletRequest httpServletRequest) {

		Map<Long, DSRequest> dsRequests =
			DSCommerceOrderAttachmentUtil.getDSRequests(
				commerceOrder, httpServletRequest);

		if (dsRequests.isEmpty()) {
			return Collections.emptyMap();
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		User user = themeDisplay.getUser();

		List<String> signableIds = new ArrayList<>();

		for (Map.Entry<Long, DSRequest> entry : dsRequests.entrySet()) {
			DSRequest dsRequest = entry.getValue();

			if (dsRequest.isSignatureRequired(user.getEmailAddress())) {
				signableIds.add(String.valueOf(entry.getKey()));
			}
		}

		return HashMapBuilder.<String, Object>put(
			"signableIds", signableIds
		).put(
			"signatureStatuses",
			DSCommerceOrderAttachmentUtil.getSignatureStatuses(dsRequests)
		).build();
	}

	@Override
	public List<FDSActionDropdownItem> getFDSActionDropdownItems(
		CommerceOrder commerceOrder, HttpServletRequest httpServletRequest) {

		Map<Long, DSRequest> dsRequests =
			DSCommerceOrderAttachmentUtil.getDSRequests(
				commerceOrder, httpServletRequest);

		if (dsRequests.isEmpty()) {
			return Collections.emptyList();
		}

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		User user = themeDisplay.getUser();

		boolean signatureRequired = false;

		for (DSRequest dsRequest : dsRequests.values()) {
			if (dsRequest.isSignatureRequired(user.getEmailAddress())) {
				signatureRequired = true;

				break;
			}
		}

		List<FDSActionDropdownItem> fdsActionDropdownItems = new ArrayList<>();

		if (signatureRequired) {
			fdsActionDropdownItems.add(
				FDSActionDropdownItemBuilder.putData(
					"signURL",
					DSCommerceOrderAttachmentUtil.getActionURL(
						"/commerce-digital-signature/sign", themeDisplay)
				).setHref(
					StringPool.POUND
				).setIcon(
					"pencil"
				).setLabel(
					_language.get(httpServletRequest, "sign")
				).build(
					"sign"
				));
		}

		fdsActionDropdownItems.add(
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

		return fdsActionDropdownItems;
	}

	@Reference
	private Language _language;

}
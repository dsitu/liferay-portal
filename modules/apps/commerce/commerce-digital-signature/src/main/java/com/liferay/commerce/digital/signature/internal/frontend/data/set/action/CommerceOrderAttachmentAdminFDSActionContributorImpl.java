/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.frontend.data.set.action;

import com.liferay.commerce.digital.signature.internal.util.DSCommerceOrderAttachmentUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderAttachmentAdminFDSActionContributor;
import com.liferay.frontend.data.set.model.FDSActionDropdownItem;
import com.liferay.frontend.data.set.model.FDSActionDropdownItemBuilder;
import com.liferay.frontend.data.set.model.FDSActionDropdownItemList;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.servlet.http.HttpServletRequest;

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

		return HashMapBuilder.<String, Object>put(
			"signatureStatuses",
			DSCommerceOrderAttachmentUtil.getSignatureStatuses(
				DSCommerceOrderAttachmentUtil.getDSRequests(
					commerceOrder, httpServletRequest))
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

	@Reference
	private Language _language;

}
/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.url;

import com.liferay.digital.signature.constants.DigitalSignaturePortletKeys;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutConstants;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.product.navigation.personal.menu.util.PersonalApplicationURLUtil;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(service = SignDSURLProvider.class)
public class SignDSURLProviderImpl implements SignDSURLProvider {

	@Override
	public String getURL(long companyId, long siteId, long dsRequestId)
		throws PortalException {

		return _getURL(
			companyId, siteId, "/-/digital_signature/sign/" + dsRequestId);
	}

	private Layout _fetchLayout(long siteId) throws PortalException {
		if (siteId <= 0) {
			return null;
		}

		long plid = _portal.getPlidFromPortletId(
			siteId, DigitalSignaturePortletKeys.SIGN_DIGITAL_SIGNATURE);

		if (plid == LayoutConstants.DEFAULT_PLID) {
			return null;
		}

		return _layoutLocalService.fetchLayout(plid);
	}

	private Group _getGroup(long companyId, long siteId)
		throws PortalException {

		if (siteId > 0) {
			return _groupLocalService.getGroup(siteId);
		}

		return _groupLocalService.getGroup(companyId, GroupConstants.GUEST);
	}

	private String _getURL(long companyId, long siteId, String path)
		throws PortalException {

		Company company = _companyLocalService.getCompany(companyId);

		Layout layout = _fetchLayout(siteId);

		if (layout != null) {
			Group group = layout.getGroup();

			String pathFriendlyURL = _portal.getPathFriendlyURLPublic();

			if (layout.isPrivateLayout()) {
				pathFriendlyURL = _portal.getPathFriendlyURLPrivateGroup();
			}

			return StringBundler.concat(
				company.getPortalURL(siteId), pathFriendlyURL,
				group.getFriendlyURL(), layout.getFriendlyURL(), path);
		}

		Group group = _getGroup(companyId, siteId);

		layout =
			PersonalApplicationURLUtil.
				getOrAddEmbeddedPersonalApplicationLayout(
					_userLocalService.getGuestUser(companyId), group, false);

		return StringBundler.concat(
			company.getPortalURL(group.getGroupId()),
			_portal.getPathFriendlyURLPublic(), group.getFriendlyURL(),
			layout.getFriendlyURL(), path);
	}

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private UserLocalService _userLocalService;

}
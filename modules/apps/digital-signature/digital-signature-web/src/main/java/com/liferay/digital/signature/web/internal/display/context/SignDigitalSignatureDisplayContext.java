/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.web.internal.display.context;

import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

/**
 * @author Danny Situ
 */
public class SignDigitalSignatureDisplayContext {

	public SignDigitalSignatureDisplayContext(
		DSRequestManager dsRequestManager, RenderRequest renderRequest,
		RenderResponse renderResponse) {

		_dsRequestManager = dsRequestManager;
		_renderRequest = renderRequest;
		_renderResponse = renderResponse;

		_themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);
	}

	public boolean isSignable() {
		DSRequest dsRequest = _getDSRequest();

		if (dsRequest == null) {
			return false;
		}

		User user = _themeDisplay.getUser();

		return dsRequest.isSignatureRequired(user.getEmailAddress());
	}

	private DSRequest _getDSRequest() {
		if (_dsRequest != null) {
			return _dsRequest;
		}

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			ParamUtil.getLong(_renderRequest, "dsRequestId"));

		if ((dsRequest == null) ||
			(dsRequest.getCompanyId() != _themeDisplay.getCompanyId())) {

			return null;
		}

		_dsRequest = dsRequest;

		return _dsRequest;
	}

	private DSRequest _dsRequest;
	private final DSRequestManager _dsRequestManager;
	private final RenderRequest _renderRequest;
	private final RenderResponse _renderResponse;
	private final ThemeDisplay _themeDisplay;

}
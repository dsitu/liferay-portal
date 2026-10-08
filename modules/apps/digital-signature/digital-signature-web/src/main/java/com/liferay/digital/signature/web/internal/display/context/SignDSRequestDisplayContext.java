/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.web.internal.display.context;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureSystemConfigurationUtil;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.url.builder.PortletURLBuilder;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import java.util.Map;

/**
 * @author Danny Situ
 */
public class SignDSRequestDisplayContext {

	public SignDSRequestDisplayContext(
		DigitalSignatureConfiguration digitalSignatureConfiguration,
		DSRequest dsRequest, RenderRequest renderRequest,
		RenderResponse renderResponse, String signingURL) {

		_digitalSignatureConfiguration = digitalSignatureConfiguration;
		_dsRequest = dsRequest;
		_renderRequest = renderRequest;
		_renderResponse = renderResponse;
		_signingURL = signingURL;

		_themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);
	}

	public String getBackURL() {
		if (_backURL != null) {
			return _backURL;
		}

		_backURL = PortalUtil.escapeRedirect(
			ParamUtil.getString(_renderRequest, "backURL"));

		if (Validator.isNotNull(_backURL)) {
			return _backURL;
		}

		Group group = GroupLocalServiceUtil.fetchGroup(
			_dsRequest.getSiteGroupId());

		if (group != null) {
			_backURL = group.getDisplayURL(_themeDisplay);

			if (Validator.isNotNull(_backURL)) {
				return _backURL;
			}
		}

		_backURL = _themeDisplay.getURLHome();

		return _backURL;
	}

	public Map<String, Object> getProps() {
		return HashMapBuilder.<String, Object>put(
			"backURL", getBackURL()
		).put(
			"completeURL",
			PortletURLBuilder.createActionURL(
				_renderResponse
			).setActionName(
				"/digital_signature/complete_ds_recipient_view"
			).setBackURL(
				getBackURL()
			).setParameter(
				"dsRequestId", _dsRequest.getDSRequestId()
			).buildString()
		).put(
			"containerId", _renderResponse.getNamespace() + "signingContainer"
		).put(
			"integrationKey", _digitalSignatureConfiguration.integrationKey()
		).put(
			"jsURL",
			DigitalSignatureSystemConfigurationUtil.getJSURL(
				_digitalSignatureConfiguration.environment())
		).put(
			"signingURL", _signingURL
		).build();
	}

	private String _backURL;
	private final DigitalSignatureConfiguration _digitalSignatureConfiguration;
	private final DSRequest _dsRequest;
	private final RenderRequest _renderRequest;
	private final RenderResponse _renderResponse;
	private final String _signingURL;
	private final ThemeDisplay _themeDisplay;

}
/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.commerce.order.content.web.internal.portlet.configuration.admin.display;

import com.liferay.commerce.order.content.web.internal.display.context.OpenCommerceOrderContentConfigurationDisplayContext;
import com.liferay.commerce.order.content.web.internal.portlet.configuration.OpenCommerceOrderContentPortletInstanceConfiguration;
import com.liferay.configuration.admin.display.ConfigurationFormRenderer;
import com.liferay.frontend.taglib.servlet.taglib.util.JSPRenderer;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ParamUtil;

import java.io.IOException;

import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	configurationPid = "com.liferay.commerce.order.content.web.internal.portlet.configuration.OpenCommerceOrderContentPortletInstanceConfiguration",
	immediate = true, service = ConfigurationFormRenderer.class
)
public class OpenCommerceOrderContentPortletInstanceConfigurationFormRenderer
	implements ConfigurationFormRenderer {

	@Override
	public String getPid() {
		return OpenCommerceOrderContentPortletInstanceConfiguration.class.
			getName();
	}

	@Override
	public Map<String, Object> getRequestParameters(
		HttpServletRequest httpServletRequest) {

		return HashMapBuilder.<String, Object>put(
			"displayStyle",
			ParamUtil.getString(httpServletRequest, "displayStyle")
		).put(
			"displayStyleGroupId",
			ParamUtil.getInteger(httpServletRequest, "displayStyleGroupId")
		).build();
	}

	@Override
	public void render(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		OpenCommerceOrderContentConfigurationDisplayContext
			openCommerceOrderContentConfigurationDisplayContext =
				new OpenCommerceOrderContentConfigurationDisplayContext();

		openCommerceOrderContentConfigurationDisplayContext.setDisplayStyle(
			_openCommerceOrderContentPortletInstanceConfiguration.
				displayStyle());
		openCommerceOrderContentConfigurationDisplayContext.
			setDisplayStyleGroupId(
				_openCommerceOrderContentPortletInstanceConfiguration.
					displayStyleGroupId());

		httpServletRequest.setAttribute(
			OpenCommerceOrderContentConfigurationDisplayContext.class.getName(),
			openCommerceOrderContentConfigurationDisplayContext);

		_jspRenderer.renderJSP(
			_servletContext, httpServletRequest, httpServletResponse,
			"/pending_commerce_orders/admin/configuration.jsp");
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_openCommerceOrderContentPortletInstanceConfiguration =
			ConfigurableUtil.createConfigurable(
				OpenCommerceOrderContentPortletInstanceConfiguration.class,
				properties);
	}

	@Reference
	private JSPRenderer _jspRenderer;

	private volatile OpenCommerceOrderContentPortletInstanceConfiguration
		_openCommerceOrderContentPortletInstanceConfiguration;

	@Reference(
		target = "(osgi.web.symbolicname=com.liferay.commerce.order.content.web)",
		unbind = "-"
	)
	private ServletContext _servletContext;

}
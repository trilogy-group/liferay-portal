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

package com.liferay.dynamic.data.mapping.taglib.servlet.taglib.base;

import com.liferay.dynamic.data.mapping.taglib.internal.servlet.ServletContextUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

/**
 * @author Bruno Basto
 * @generated
 */
public abstract class BaseHTMLTag extends com.liferay.taglib.util.IncludeTag {

	@Override
	public int doStartTag() throws JspException {
		setAttributeNamespace(_ATTRIBUTE_NAMESPACE);

		return super.doStartTag();
	}

	public boolean getCheckRequired() {
		return _checkRequired;
	}

	public long getClassNameId() {
		return _classNameId;
	}

	public long getClassPK() {
		return _classPK;
	}

	public com.liferay.dynamic.data.mapping.storage.DDMFormValues getDdmFormValues() {
		return _ddmFormValues;
	}

	public java.lang.String getFieldsNamespace() {
		return _fieldsNamespace;
	}

	public boolean getIgnoreRequestValue() {
		return _ignoreRequestValue;
	}

	public boolean getLocalizable() {
		return _localizable;
	}

	public boolean getReadOnly() {
		return _readOnly;
	}

	public boolean getRepeatable() {
		return _repeatable;
	}

	public java.util.Locale getRequestedLocale() {
		return _requestedLocale;
	}

	public boolean getShowEmptyFieldLabel() {
		return _showEmptyFieldLabel;
	}

	public void setCheckRequired(boolean checkRequired) {
		_checkRequired = checkRequired;
	}

	public void setClassNameId(long classNameId) {
		_classNameId = classNameId;
	}

	public void setClassPK(long classPK) {
		_classPK = classPK;
	}

	public void setDdmFormValues(com.liferay.dynamic.data.mapping.storage.DDMFormValues ddmFormValues) {
		_ddmFormValues = ddmFormValues;
	}

	public void setFieldsNamespace(java.lang.String fieldsNamespace) {
		_fieldsNamespace = fieldsNamespace;
	}

	public void setIgnoreRequestValue(boolean ignoreRequestValue) {
		_ignoreRequestValue = ignoreRequestValue;
	}

	public void setLocalizable(boolean localizable) {
		_localizable = localizable;
	}

	public void setReadOnly(boolean readOnly) {
		_readOnly = readOnly;
	}

	public void setRepeatable(boolean repeatable) {
		_repeatable = repeatable;
	}

	public void setRequestedLocale(java.util.Locale requestedLocale) {
		_requestedLocale = requestedLocale;
	}

	public void setShowEmptyFieldLabel(boolean showEmptyFieldLabel) {
		_showEmptyFieldLabel = showEmptyFieldLabel;
	}

	@Override
	public void setPageContext(PageContext pageContext) {
		super.setPageContext(pageContext);

		setServletContext(ServletContextUtil.getServletContext());
	}

	@Override
	protected void cleanUp() {
		super.cleanUp();

		_checkRequired = true;
		_classNameId = 0;
		_classPK = 0;
		_ddmFormValues = null;
		_fieldsNamespace = null;
		_ignoreRequestValue = false;
		_localizable = true;
		_readOnly = false;
		_repeatable = true;
		_requestedLocale = null;
		_showEmptyFieldLabel = true;
	}

	@Override
	protected String getEndPage() {
		return _END_PAGE;
	}

	@Override
	protected String getStartPage() {
		return _START_PAGE;
	}

	@Override
	protected void setAttributes(HttpServletRequest request) {
		request.setAttribute("liferay-ddm:html:checkRequired", String.valueOf(_checkRequired));
		request.setAttribute("liferay-ddm:html:classNameId", String.valueOf(_classNameId));
		request.setAttribute("liferay-ddm:html:classPK", String.valueOf(_classPK));
		request.setAttribute("liferay-ddm:html:ddmFormValues", _ddmFormValues);
		request.setAttribute("liferay-ddm:html:fieldsNamespace", _fieldsNamespace);
		request.setAttribute("liferay-ddm:html:ignoreRequestValue", String.valueOf(_ignoreRequestValue));
		request.setAttribute("liferay-ddm:html:localizable", String.valueOf(_localizable));
		request.setAttribute("liferay-ddm:html:readOnly", String.valueOf(_readOnly));
		request.setAttribute("liferay-ddm:html:repeatable", String.valueOf(_repeatable));
		request.setAttribute("liferay-ddm:html:requestedLocale", _requestedLocale);
		request.setAttribute("liferay-ddm:html:showEmptyFieldLabel", String.valueOf(_showEmptyFieldLabel));
	}

	protected static final String _ATTRIBUTE_NAMESPACE = "liferay-ddm:html:";

	private static final String _END_PAGE =
		"/html/end.jsp";

	private static final String _START_PAGE =
		"/html/start.jsp";

	private boolean _checkRequired = true;
	private long _classNameId = 0;
	private long _classPK = 0;
	private com.liferay.dynamic.data.mapping.storage.DDMFormValues _ddmFormValues = null;
	private java.lang.String _fieldsNamespace = null;
	private boolean _ignoreRequestValue = false;
	private boolean _localizable = true;
	private boolean _readOnly = false;
	private boolean _repeatable = true;
	private java.util.Locale _requestedLocale = null;
	private boolean _showEmptyFieldLabel = true;

}
package org.thymeleaf.dialect.springdata.util;

import static org.thymeleaf.dialect.springdata.util.Strings.AND;
import static org.thymeleaf.dialect.springdata.util.Strings.COMMA;
import static org.thymeleaf.dialect.springdata.util.Strings.EMPTY;
import static org.thymeleaf.dialect.springdata.util.Strings.EQ;
import static org.thymeleaf.dialect.springdata.util.Strings.PAGE;
import static org.thymeleaf.dialect.springdata.util.Strings.Q_MARK;
import static org.thymeleaf.dialect.springdata.util.Strings.SIZE;
import static org.thymeleaf.dialect.springdata.util.Strings.SORT;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.dialect.springdata.Keys;
import org.thymeleaf.dialect.springdata.exception.InvalidObjectParameterException;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.servlet.IServletWebRequest;
import org.unbescape.html.HtmlEscape;

public final class PageUtils {

    private PageUtils() {
    }

    public static Page<?> findPage(final ITemplateContext context) {
        // 1. Get Page object from local variables (defined with sd:page-object)
        // 2. Search Page using ${page} expression
        // 3. Search Page object as request attribute

        final Object pageFromLocalVariable = context.getVariable(Keys.PAGE_VARIABLE_KEY);
        if (isPageInstance(pageFromLocalVariable)) {
            return (Page<?>) pageFromLocalVariable;
        }

        // Check if not null and Page instance available with ${page} expression
        final IEngineConfiguration configuration = context.getConfiguration();
        final IStandardExpressionParser parser = StandardExpressions.getExpressionParser(configuration);
        final IStandardExpression expression = parser.parseExpression(context, Keys.PAGE_EXPRESSION);
        final Object page = expression.execute(context);
        if (isPageInstance(page)) {
            return (Page<?>) page;
        }

        // Search for Page object, and only one instance, as request attribute
        if (context instanceof IWebContext) {
            IWebExchange webExchange = ((IWebContext) context).getExchange();
            Set<String> attrNames = webExchange.getAllAttributeNames();
            Page<?> pageOnRequest = null;

            for (String attrName : attrNames) {
                Object attr = webExchange.getAttributeValue(attrName);
                if (isPageInstance(attr)) {
                    if (pageOnRequest != null) {
                        throw new InvalidObjectParameterException("More than one Page object found on request!");
                    }

                    pageOnRequest = (Page<?>) attr;
                }
            }

            if (pageOnRequest != null) {
                return pageOnRequest;
            }
        }

        throw new InvalidObjectParameterException("Invalid or not present Page object found on request!");
    }

    public static String createPageUrl(final ITemplateContext context, int pageNumber) {

	    	final String jsFunction = (String) context.getVariable(Keys.PAGINATION_JS_FUNCTION_KEY);
	      if (jsFunction != null) {
	      	return getJavaScriptURL(jsFunction, new StringBuilder().append(PAGE).append(EQ).append(pageNumber).toString());
	      }

        final String prefix = getParamPrefix(context);
        final Collection<String> excludedParams = Arrays.asList(new String[] { prefix.concat(PAGE) });
        final String baseUrl = buildBaseUrl(context, excludedParams);

        return buildUrl(baseUrl, context).append(PAGE).append(EQ).append(pageNumber).toString();
    }

    /**
     * Creates an url to sort data by fieldName
     * 
     * @param context execution context
     * @param fieldName field name to sort
     * @param forcedDir optional, if specified then only this sort direction will be allowed
     * @return sort URL
     */
    public static String createSortUrl(final ITemplateContext context, final String fieldName, final Direction forcedDir) {

        final StringBuilder sortParam = new StringBuilder();
        final Page<?> page = findPage(context);
        final Sort sort = page.getSort();
        final boolean hasPreviousOrder = sort != null && sort.getOrderFor(fieldName) != null;
        if (forcedDir != null) {
            sortParam.append(fieldName).append(COMMA).append(forcedDir.toString().toLowerCase());
        } else if (hasPreviousOrder) {
            // Sort parameters exists for this field, modify direction
            Order previousOrder = sort.getOrderFor(fieldName);
            Direction dir = previousOrder.isAscending() ? Direction.DESC : Direction.ASC;
            sortParam.append(fieldName).append(COMMA).append(dir.toString().toLowerCase());
        } else {
            sortParam.append(fieldName);
        }

        final String jsFunction = (String) context.getVariable(Keys.PAGINATION_JS_FUNCTION_KEY);
        if (jsFunction != null) {
        	return getJavaScriptURL(jsFunction, new StringBuilder().append(SORT).append(EQ).append(sortParam).toString());
        }

        // Params can be prefixed to manage multiple pagination on the same page
        final String prefix = getParamPrefix(context);
        final Collection<String> excludedParams = Arrays
                .asList(new String[] { prefix.concat(SORT), prefix.concat(PAGE) });
        final String baseUrl = buildBaseUrl(context, excludedParams);

        return buildUrl(baseUrl, context).append(SORT).append(EQ).append(sortParam).toString();
    }

    public static String createPageSizeUrl(final ITemplateContext context, int pageSize) {

	    	final String jsFunction = (String) context.getVariable(Keys.PAGINATION_JS_FUNCTION_KEY);
	      if (jsFunction != null) {
	      	return getJavaScriptURL(jsFunction, new StringBuilder().append(SIZE).append(EQ).append(pageSize).toString());
	      }

        final String prefix = getParamPrefix(context);
        // Reset page number to avoid empty lists
        final Collection<String> excludedParams = Arrays
                .asList(new String[] { prefix.concat(SIZE), prefix.concat(PAGE) });
        final String baseUrl = buildBaseUrl(context, excludedParams);

        return buildUrl(baseUrl, context).append(SIZE).append(EQ).append(pageSize).toString();
    }

    public static int getFirstItemInPage(final Page<?> page) {
        return page.getSize() * page.getNumber() + 1;
    }

    public static int getLatestItemInPage(final Page<?> page) {
        return page.getSize() * page.getNumber() + page.getNumberOfElements();
    }
    
    public static boolean isFirstPage(Page<?> page) {
    		if( page.getTotalPages()==0 ) {
    			return true;
    		}
    		
    		return page.isFirst();
    }
    
    public static boolean hasPrevious(Page<?> page) {
    		return page.getTotalPages()>0 && page.hasPrevious();
    }

    private static String buildBaseUrl(final ITemplateContext context, Collection<String> excludeParams) {
        // URL defined with pagination-url tag
        final String url = (String) context.getVariable(Keys.PAGINATION_URL_KEY);

        if (url == null && context instanceof IWebContext) {
            // Creates url from actual request URI and parameters
            final StringBuilder builder = new StringBuilder();
            final IWebContext webContext = (IWebContext) context;
            final IWebExchange webExchange = webContext.getExchange();
            final IWebRequest request = webExchange.getRequest();

            // URL base path from request
            builder.append(getRequestURI(request));

            Map<String, String[]> params = request.getParameterMap();
            Set<Entry<String, String[]>> entries = params.entrySet();
            boolean firstParam = true;
            for (Entry<String, String[]> param : entries) {
                // Append params not excluded to basePath
                String name = param.getKey();
                if (!excludeParams.contains(name)) {
                    if (firstParam) {
                        builder.append(Q_MARK);
                        firstParam = false;
                    } else {
                        builder.append(AND);
                    }

                    // Iterate over all values to create multiple values per
                    // parameter
                    String[] values = param.getValue();
                    Collection<String> paramValues = Arrays.asList(values);
                    Iterator<String> it = paramValues.iterator();
                    while (it.hasNext()) {
                        String value = it.next();
                        builder.append(name).append(EQ).append(value);
                        if (it.hasNext()) {
                            builder.append(AND);
                        }
                    }
                }
            }

            // Escape to HTML content
            return HtmlEscape.escapeHtml4Xml(builder.toString());
        }

        return url == null ? EMPTY : url;
    }

    private static String getRequestURI(IWebRequest webRequest) {
        if (webRequest instanceof IServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequestURI();
        } else  {
            // from org.thymeleaf.web.IWebRequest.getRequestURL
            String scheme = webRequest.getScheme();
            String serverName = webRequest.getServerName();
            Integer serverPort = webRequest.getServerPort();
            String requestPath = webRequest.getRequestPath();
            if (scheme != null && serverName != null && serverPort != null) {
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append(scheme).append("://").append(serverName);
                if ((!scheme.equals("http") || serverPort != 80) && (!scheme.equals("https") || serverPort != 443)) {
                    urlBuilder.append(':').append(serverPort);
                }

                urlBuilder.append(requestPath);

                return urlBuilder.toString();
            } else {
                throw new UnsupportedOperationException("Request scheme, server name or port are null in this environment. Cannot compute request URL");
            }
        }
    }

    private static boolean isPageInstance(Object page) {
        return page != null && (page instanceof Page<?>);
    }

    private static StringBuilder buildUrl(String baseUrl, final ITemplateContext context) {
        final String paramAppender = String.valueOf(baseUrl).contains(Q_MARK) ? AND : Q_MARK;
        final String prefix = getParamPrefix(context);

        return new StringBuilder(baseUrl).append(paramAppender).append(prefix);
    }

    private static String getParamPrefix(final ITemplateContext context) {
        final String prefix = (String) context.getVariable(Keys.PAGINATION_QUALIFIER_PREFIX);

        return prefix == null ? EMPTY : prefix.concat("_");
    }

    private static String getJavaScriptURL(String functionName, String params) {
    		return Strings.concat(
    				Strings.JAVASCRIPT_VOID_0,
    				Strings.DOUBLE_QUOTE,
    				Strings.BLANK,
    				Strings.ONCLICK,
    				Strings.EQ,
    				Strings.DOUBLE_QUOTE,
    				functionName,
    				Strings.PARENTHESIS_OPEN,
    				Strings.SINGLE_QUOTE,
    				params,
    				Strings.SINGLE_QUOTE,
    				Strings.COMMA,
    				Strings.THIS,
    				Strings.PARENTHESIS_CLOSE
    	  );
    }

}

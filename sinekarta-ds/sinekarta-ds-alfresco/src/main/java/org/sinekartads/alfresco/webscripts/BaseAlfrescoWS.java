package org.sinekartads.alfresco.webscripts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.sinekartads.alfresco.util.NodeTools;
import org.sinekartads.dto.BaseDTO;
import org.sinekartads.dto.ResultCode;
import org.sinekartads.dto.domain.NodeDTO;
import org.sinekartads.dto.request.BaseRequest;
import org.sinekartads.dto.response.BaseResponse;
import org.sinekartads.util.HexUtils;
import org.sinekartads.util.TemplateUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.GenericTypeResolver;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.util.Assert;

public abstract class BaseAlfrescoWS<Request extends BaseRequest, Response extends BaseResponse>
		extends DeclarativeWebScript implements InitializingBean {

	static final String REQUEST_TYPE = "requestType";

	@SuppressWarnings("unchecked")
	public BaseAlfrescoWS() {
		Class<?>[] typeArgs = GenericTypeResolver.resolveTypeArguments(getClass(), BaseAlfrescoWS.class);
		for (Class<?> typeArg : typeArgs) {
			if (BaseRequest.class.isAssignableFrom(typeArg)) {
				this.requestClass = (Class<Request>) typeArg;
			}
		}
		for (Class<?> typeArg : typeArgs) {
			if (BaseResponse.class.isAssignableFrom(typeArg)) {
				this.responseClass = (Class<Response>) typeArg;
			}
		}
	}
	
	private Class<Request> requestClass;
	private Class<Response> responseClass;
	protected Logger tracer = Logger.getLogger(getClass());
	protected ServiceRegistry serviceRegistry;
	
	protected NodeService nodeService;
	protected ActionService actionService;
	protected ContentService contentService;
	protected AuthorityService authorityService;
	protected AuthenticationService authenticationService;
	protected PersonService personService;

	@Override
	public void afterPropertiesSet() throws Exception {
		if(serviceRegistry != null) {
			nodeService = serviceRegistry.getNodeService();
			authenticationService = serviceRegistry.getAuthenticationService();
			personService = serviceRegistry.getPersonService();
			actionService = serviceRegistry.getActionService();
			contentService = serviceRegistry.getContentService();
			authorityService = serviceRegistry.getAuthorityService();
		}
	}
	
	public void setServiceRegistry(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}
	
	@Override
	protected Map<String, Object> executeImpl(WebScriptRequest wsReq, Status status, Cache cache) {
		
		if (tracer.isDebugEnabled())
			tracer.debug("webscript " + getClass().getName() + " starting");
		
		Map<String, Object> model = new HashMap<String, Object>();
		String requestType = wsReq.getParameter(REQUEST_TYPE);
		
		Response resp = null;
		try {
			resp = (Response)responseClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			tracer.error ( e.getMessage(), e );
			model.put ( "results", "unable to generate the response object" );
		}
		
		try {
			Request req = null;
			if  ( StringUtils.equalsIgnoreCase(requestType, "xml") ) {
				req = (Request) BaseDTO.fromXML(wsReq.getContent().getInputStream(), requestClass);
			} else if (StringUtils.equalsIgnoreCase(requestType, "json") ) {
				req = (Request) BaseDTO.fromJSON(wsReq.getContent().getInputStream(), requestClass);
			}
			
			// Esegue la business logic per generare il response
			resp = executeImpl(req, status, cache);
		} catch(Exception e) {
			processError(resp, e);
		} finally {
			// Formatta e restituisce il response
			String res;
			if (StringUtils.equalsIgnoreCase(requestType, "xml")) {
				res = resp.toXML();
			} else {
				res = resp.toJSON();
			}
			model.put("results", res);
		}
		
		if (tracer.isDebugEnabled())
			tracer.debug("webscript " + getClass().getName() + " finished");
		return model;
	}

	
	
	protected abstract Response executeImpl(Request req, Status status, Cache cache) ;
	
	protected void processError ( Response resp, Exception e ) {
		processError ( resp, e, null );
	}
	
	protected void processError ( Response resp, Exception e, String format ) {
		String errorMessage = e.getMessage();
		if ( StringUtils.isNotBlank(errorMessage) ) {
			errorMessage = e.toString();
		}
		if ( StringUtils.isBlank(format) ) {
			format = "%s";
		}
		String message = String.format ( format, errorMessage );
		resp.setMessage ( message );
		resp.resultCodeToString( ResultCode.INTERNAL_SERVER_ERROR );
		tracer.error(message, e);
	}
	
	
	
	// Utility methods
	
	protected String[] currentAuthorities() {
		String currentUserName = authenticationService.getCurrentUserName ( );
		return TemplateUtils.Conversion.collectionToArray (
				authorityService.getAuthoritiesForUser ( currentUserName ) );
	}
	
	

	protected void storeIntoNode ( 
			NodeDTO node, 
			String hex ) {
		
		if ( StringUtils.isBlank(hex) ) {
			hex = "";
		}
		
		if ( node != null ) {
			NodeRef nodeRef;
			Assert.isTrue( StringUtils.isNotBlank(node.getFileName()) );
			if ( StringUtils.isBlank(node.getNodeRef()) ) {
				Assert.isTrue( StringUtils.isNotBlank(node.getParentRef()) );
				NodeRef parentRef = new NodeRef ( node.getParentRef() ); 
				nodeRef = NodeTools.createNode ( nodeService, parentRef, node.getFileName() );
				node.setNodeRef ( nodeRef.getId() );
			} else {
				nodeRef = new NodeRef ( node.getNodeRef() );
				Assert.isTrue ( nodeService.exists(nodeRef) );
			}
			
			byte[] output = HexUtils.decodeHex ( hex );
			ContentWriter writer = contentService.getWriter ( 
					nodeRef, ContentModel.PROP_CONTENT, true );
			writer.putContent ( new ByteArrayInputStream ( output ) );
		}
	}
	
	protected String loadFromNode( NodeDTO node ) throws IOException {
		String contentHex = null;
		InputStream is = null;
		if ( node != null && StringUtils.isNotBlank(node.getNodeRef()) ) {
			try {
				NodeRef nodeRef = new NodeRef ( node.getNodeRef() );
				ContentReader contentReader = contentService.getReader(
						nodeRef, ContentModel.PROP_CONTENT);
				is = contentReader.getContentInputStream();
				contentHex = HexUtils.encodeHex(IOUtils.toByteArray(is));
			} finally {
				IOUtils.closeQuietly(is);
			}
		}
		return contentHex;
	}
}
/** 
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements. See the NOTICE file 
 * distributed with this work for additional information 
 * regarding copyright ownership. The ASF licenses this file 
 * to you under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance 
 * with the License. You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied. See the License for the 
 * specific language governing permissions and limitations 
 * under the License. 
 */
package org.apache.cxf.dosgi.dsw.handlers;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.dosgi.dsw.Constants;
import org.apache.cxf.dosgi.dsw.OsgiUtils;
import org.apache.cxf.dosgi.dsw.service.ExportRegistrationImpl;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.remoteserviceadmin.EndpointDescription;

public class JaxRSHttpServiceConfigurationTypeHandler extends HttpServiceConfigurationTypeHandler {
    private static final Logger LOG = Logger.getLogger(JaxRSHttpServiceConfigurationTypeHandler.class
        .getName());

    Set<ServiceReference> httpServiceReferences = new CopyOnWriteArraySet<ServiceReference>();

    protected JaxRSHttpServiceConfigurationTypeHandler(BundleContext dswBC,

    Map<String, Object> handlerProps) {
        super(dswBC, handlerProps);
    }

    @Override
    public void createServer(ExportRegistrationImpl exportRegistration, BundleContext dswContext,
                             BundleContext callingContext, Map sd, Class<?> iClass, Object serviceBean) {

        String contextRoot = getServletContextRoot(sd, iClass);
        if (contextRoot == null) {
            LOG.warning("Remote address is unavailable");
            return;
        }

        CXFNonSpringServlet cxf = new CXFNonSpringServlet();
        HttpService httpService = getHttpService();
        try {
            HttpContext httpContext = getHttpContext(dswContext, httpService);
            httpService.registerServlet(contextRoot, cxf, new Hashtable<String, String>(), httpContext);
            registerUnexportHook(exportRegistration, contextRoot);
            LOG.info("Successfully registered CXF DOSGi servlet at " + contextRoot);
        } catch (Exception e) {
            throw new ServiceException("CXF DOSGi: problem registering CXF HTTP Servlet", e);
        }
        Bus bus = cxf.getBus();

        JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
        factory.setBus(bus);

        List<UserResource> resources = JaxRSUtils.getModel(callingContext, iClass);
        if (resources != null) {
            factory.setModelBeansWithServiceClass(resources, iClass);
            factory.setServiceBeanObjects(serviceBean);
        } else {
            factory.setServiceClass(iClass);
            factory.setResourceProvider(iClass, new SingletonResourceProvider(serviceBean));
        }

        factory.setAddress("/");
        List<Object> providers = JaxRSUtils.getProviders(callingContext, dswContext, sd);
        if (providers != null && providers.size() > 0) {
            factory.setProviders(providers);
        }

        String address = constructAddress(dswContext, contextRoot);

        

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            String[] intents = new String[] {
                "HTTP"
            };
            
            // The properties for the EndpointDescription
            Map<String, Object> endpointProps = createEndpointProps(sd, iClass, new String[] {
                Constants.RS_CONFIG_TYPE
            }, address,intents);
            EndpointDescription endpdDesc = null;
            
            
            Thread.currentThread().setContextClassLoader(JAXRSServerFactoryBean.class.getClassLoader());
            Server server = factory.create();
            registerStopHook(bus, httpService, server, contextRoot, Constants.RS_HTTP_SERVICE_CONTEXT);

            endpdDesc = new EndpointDescription(endpointProps);
            exportRegistration.setServer(server);
            
            // add the information on the new Endpoint to the export registration
            exportRegistration.setEndpointdescription(endpdDesc);
        } catch (IntentUnsatifiedException iue) {
            exportRegistration.setException(iue);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }


    }

    @Override
    protected String getServletContextRoot(Map sd, Class<?> iClass) {
        String context = OsgiUtils.getProperty(sd, Constants.RS_HTTP_SERVICE_CONTEXT);

        if (context == null) {
            context = "/" + iClass.getName().replace('.', '/');
            LOG.info("Using a default address : " + context);
        }
        return context;
    }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.admin.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.dubbo.admin.annotation.Authority;
import org.apache.dubbo.admin.common.util.Constants;
import org.apache.dubbo.admin.common.util.Tool;
import org.apache.dubbo.admin.model.domain.Consumer;
import org.apache.dubbo.admin.model.domain.Provider;
import org.apache.dubbo.admin.model.dto.MetricDTO;
import org.apache.dubbo.admin.model.dto.RelationDTO;
import org.apache.dubbo.admin.service.ConsumerService;
import org.apache.dubbo.admin.service.MetricsService;
import org.apache.dubbo.admin.service.ProviderService;
import org.apache.dubbo.admin.service.impl.MetrcisCollectServiceImpl;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.identifier.MetadataIdentifier;
//import org.apache.dubbo.metadata.identifier.MetadataIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Authority(needLogin = true)
@RestController
@RequestMapping("/api/{env}/metrics")
public class MetricsCollectController {

    private ProviderService providerService;
    private ConsumerService consumerService;
    private MetricsService metricsService;

    @Autowired
    public MetricsCollectController(ProviderService providerService, ConsumerService consumerService, MetricsService metricsService) {
        this.providerService = providerService;
        this.consumerService = consumerService;
        this.metricsService = metricsService;
    }

    @RequestMapping(method = RequestMethod.POST)
    public String metricsCollect(@RequestParam String group, @PathVariable String env) {
        MetrcisCollectServiceImpl service = new MetrcisCollectServiceImpl();
        service.setUrl("dubbo://127.0.0.1:20880?scope=remote&cache=true");

        return service.invoke(group).toString();
    }

    @RequestMapping(value = "/relation", method = RequestMethod.GET)
    public RelationDTO getApplicationRelation(){
        return metricsService.getApplicationRelation();
    }

    private String getOnePortMessage(String group, String ip, String port, String protocol) {
        MetrcisCollectServiceImpl metrcisCollectService = new MetrcisCollectServiceImpl();
        metrcisCollectService.setUrl(protocol + "://" + ip + ":" + port +"?scope=remote&cache=true");
        String res = metrcisCollectService.invoke(group).toString();
        return res;
    }

    @RequestMapping( value = "/ipAddr", method = RequestMethod.GET)
    public List<MetricDTO> searchService(@RequestParam String ip, @RequestParam String group, @PathVariable String env) {

        List<ConfigProvider> configMap = new ArrayList<>();
        addMetricsConfigToMap(configMap, ip);

//         default value
        //if (configMap.size() <= 0) {
         //   configMap.put("20880", "dubbo");
        //}
        List<MetricDTO> metricDTOS = new ArrayList<>();
        for (ConfigProvider c : configMap) {
            String res = getOnePortMessage(group, c.ip.split(":")[0], c.port, c.protocol);
            List<MetricDTO> o = new Gson().fromJson(res, new TypeToken<List<MetricDTO>>() {}.getType());
            for(MetricDTO m : o){ m.setIp(c.ip); }
            metricDTOS.addAll(o);
        }
        return metricDTOS;
    }

    protected void addMetricsConfigToMap(List<ConfigProvider> configMap, String ip) {
        List<Provider> providers=null;
        if(isIp(ip)){
             providers = providerService.findByAddress(ip);
        }else {
            providers = providerService.findByService(ip);
        }
        for(Provider p: providers){
            String service = p.getService();
            MetadataIdentifier providerIdentifier = new MetadataIdentifier(Tool.getInterface(service), Tool.getVersion(service), Tool.getGroup(service),
                    Constants.PROVIDER_SIDE, p.getApplication());
            String metaData = providerService.getProviderMetaData(providerIdentifier);
            FullServiceDefinition providerServiceDefinition = new Gson().fromJson(metaData, FullServiceDefinition.class);
            Map<String, String> parameters = providerServiceDefinition.getParameters();
            //configMap.put(parameters.get(Constants.METRICS_PORT), parameters.get(Constants.METRICS_PROTOCOL));
            ConfigProvider configProvider = new ConfigProvider();
            configProvider.ip=p.getAddress();
            configProvider.serviceName=p.getService();
            configProvider.port=parameters.get(Constants.METRICS_PORT);
            configProvider.protocol=parameters.get(Constants.METRICS_PROTOCOL);
            configMap.add(configProvider);
        }
        List<Consumer> consumers = consumerService.findByAddress(ip);
        for(Consumer c:consumers){
            String service = c.getService();
            MetadataIdentifier consumerIdentifier = new MetadataIdentifier(Tool.getInterface(service), Tool.getVersion(service), Tool.getGroup(service),
                    Constants.CONSUMER_SIDE, c.getApplication());
            String metaData = consumerService.getConsumerMetadata(consumerIdentifier);
            Map<String, String> consumerParameters = new Gson().fromJson(metaData, Map.class);
            //configMap.put(consumerParameters.get(Constants.METRICS_PORT), consumerParameters.get(Constants.METRICS_PROTOCOL));
            ConfigProvider configProvider = new ConfigProvider();
            configProvider.ip=c.getAddress();
            configProvider.serviceName=c.getService();
            configProvider.port=consumerParameters.get(Constants.METRICS_PORT);
            configProvider.protocol=consumerParameters.get(Constants.METRICS_PROTOCOL);
            configMap.add(configProvider);
        }
    }

    private boolean isIp(String ip){
        String pattern = "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(ip);
        return m.matches();

    }

    private class ConfigProvider{
        String port;
        String protocol;
        String ip;
        String serviceName;
    }
}


import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.URL;
import java.net.URLDecoder;



@Configuration
public class ElasticSearchClient {

        //注入的ElasticSearch实例
        @Bean(name = "esClient")
        public TransportClient getclient()throws Exception {
            ClassLoader classLoader = ElasticSearchClient.class.getClassLoader();
            URL resource = classLoader.getResource("CN=sgadmin-keystore.jks");
            URL truresource = classLoader.getResource("truststore.jks");
            String keypath = URLDecoder.decode(resource.getPath(), "UTF-8");
            String trupath = URLDecoder.decode(truresource.getPath(),"UTF-8");
            //windows中路径会多个/ 如/E windows下需要打开注释
           if (keypath.startsWith("/")) {

                keypath=keypath.substring(1, keypath.length());
            }
            if (trupath.startsWith("/")) {
                trupath = trupath.substring(1, trupath.length());
            }

            Settings settings = Settings.builder()
                    .put("searchguard.ssl.transport.enabled", true)
                    .put("searchguard.ssl.transport.keystore_filepath",keypath)
                    .put("searchguard.ssl.transport.truststore_filepath",trupath)
					//这里的password去生成的证书文件 readme中找
                    .put("searchguard.ssl.transport.keystore_password", "af7385ab604031a89198")
                    .put("searchguard.ssl.transport.truststore_password", "1b925507b74e9eb71fba")
                    .put("searchguard.ssl.http.keystore_password", "af7385ab604031a89198")
                    .put("searchguard.ssl.http.truststore_password", "1b925507b74e9eb71fba")
                    .put("searchguard.ssl.transport.enforce_hostname_verification", false)
                    .put("client.transport.ignore_cluster_name", true)
                    .build();

            TransportClient client = new PreBuiltTransportClient(settings, SearchGuardSSLPlugin.class)
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("xxxxxIPxxxx"), 9300));

            client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
            return client;
        }


}


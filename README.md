# Elasticsearch
###主要是Elasticsearch使用searchguard后Java连接及安全验证

####安全验证这部分大多选择的Xpack,但Xpack收费当时就用了searchguard，但searchguard相关的文档很少，算是一点点去看官方文档解决。
###1. searchGuard与ELK的结合
1. 安全证书可以通过官方网站在线生成，填写集群节点信息。邮件发送后下载到本地。
2. 密钥证书生成后必须将DN配置到elasticsearch.yml中。证书生成方式不同DN不同，要查看DN确保正确（命令可百度查看证书DN）。同时search guard配置文件中要加入该DN账户给该DN以账户和权限。
3. 这里两个较好的博客 
	https://www.jianshu.com/p/91faac8e18cf
	https://blog.csdn.net/z1x2c34/article/details/77968955#commentBox
4. 证书文件在ops/resource中
	CN=sgadmin-keystore.jks和truststore.jks。
    clientUtil类在/service/util注册成bean.
5. 一些具体的es和安全插件的api建议去官网看，较详细。
6. 我会贴出我的相关配置文件供以参考.

###2. searchGuard与Java的结合
######1. 当es安装searchGuard后就不能直接建立client连接了
######2. 首先需要把 truststore.jks和CN=sgadmin-keystore.jks放到项目中，建立连接时需读取进行安全验证（不通过账户密码）
######3. 其次在需要指定连接参数
```
 Settings settings = Settings.builder()
                    .put("searchguard.ssl.transport.enabled", true)
                    .put("searchguard.ssl.transport.keystore_filepath",xxx)
                    .put("searchguard.ssl.transport.truststore_filepath",xxx)
                    .put("searchguard.ssl.transport.keystore_password", "af7385ab604031a89198")
                    .put("searchguard.ssl.transport.truststore_password", "1b925507b74e9eb71fba")
                    .put("searchguard.ssl.http.keystore_password", "af7385ab604031a89198")
                    .put("searchguard.ssl.http.truststore_password", "1b925507b74e9eb71fba")
                    .put("searchguard.ssl.transport.enforce_hostname_verification", false)
                    .put("client.transport.ignore_cluster_name", true)
                    .build();
```
######这里的password需要注意，具体值在你生成安全证书文件中的ReadMe.md中。

######在：
```
Common passwords                                                                            
Truststore password:  xxx                       
Admin keystore and private key password:  xxx
```
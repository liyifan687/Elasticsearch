# Elasticsearch
### 主要是Elasticsearch使用searchguard后Java连接及安全验证

#### 安全验证这部分大多选择的Xpack,但Xpack收费当时就用了searchguard，但searchguard相关的文档很少，算是一点点去看官方文档解决。
### 1. searchGuard与ELK的结合
######1.1 安全证书可以通过官方网站在线生成，填写集群节点信息。邮件发送后下载到本地。
######2.2 密钥证书生成后必须将DN配置到elasticsearch.yml中。证书生成方式不同DN不同，要查看DN确保正确（命令可百度查看证书DN）。同时search guard配置文件中要加入该DN账户给该DN以账户和权限。
######3. 这里两个较好的博客 
	https://www.jianshu.com/p/91faac8e18cf
	https://blog.csdn.net/z1x2c34/article/details/77968955#commentBox
######4. 证书文件在ops/resource中
	CN=sgadmin-keystore.jks和truststore.jks。
    clientUtil类在/service/util注册成bean.
######5. 一些具体的es和安全插件的api建议去官网看，较详细。
######6. 我会贴出我的相关配置文件以及部分代码供以参考.（由于是工作业务代码所以只能贴出主要部分）

### 2. searchGuard与Java的结合-建立client
###### 2.1. 当es安装searchGuard后就不能直接建立client连接了
###### 2.2. 首先需要把 truststore.jks和CN=sgadmin-keystore.jks放到项目中，建立连接时需读取进行安全验证（不通过账户密码）
###### 2.3. 其次在需要指定连接参数
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
###### 这里的password需要注意，具体值在你生成安全证书文件中的ReadMe.md中。

###### 在：
```
Common passwords                                                                            
Truststore password:  xxx                       
Admin keystore and private key password:  xxx
```

### 3.导入数据
###### 3.1支持json格式导入，我这里是将Bean转成json格式导入
```
         ObjectMapper mapper = new ObjectMapper();
            try {
                mapJackson = mapper.writeValueAsString(xcBean);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
```
###### 3.2导入时如果数据量较大，可以使用BulkRequestBuilder批量导入
``` 
			//指定index和type
			BulkRequestBuilder bulkRequest = esClient.prepareBulk();
            bulkRequest.add(esClient.prepareIndex("xingce", "xc").setSource(mapJackson, XContentType.JSON));
            if (count % 500 == 0) {
                bulkRequest.execute().actionGet();
                //此处新建一个bulkRequest，类似于重置效果。避免重复提交
                bulkRequest = esClient.prepareBulk();
            }
```
### 4. 搜索
###### 4.1 具体搜索API不再列出，百度即可，我使用的是bool+should
```
/**
     * 拼接查询条件
     **/
    public QueryBuilder getQb(EsPageRequestBean esPageRequestBean) {
        //多条件设置
        MatchQueryBuilder mpq1 = QueryBuilders
                .matchQuery(ES_TITLE, esPageRequestBean.getQuery());
        MatchQueryBuilder mpq2 = QueryBuilders
                .matchQuery(ES_QUESTIONS, esPageRequestBean.getQuery());
        MatchQueryBuilder mpq3 = QueryBuilders
                .matchQuery(ES_MATERIALS, esPageRequestBean.getQuery());
        QueryBuilder qb = QueryBuilders.boolQuery()
                .should(mpq1)
                .should(mpq2)
                .should(mpq3);
        return qb;
    }
```
###### 4.2 高亮显示
```
	/**
     * 拼接高亮查询
     **/
    public HighlightBuilder getHb() {
        HighlightBuilder hiBuilder = new HighlightBuilder();
		//这里设置包含高亮关键字的标签
        hiBuilder.preTags("<mark>").postTags("</mark>");
		//这里fields中设置需要高亮显示的查询字段，名字是Es中的字段名
        hiBuilder.field(ES_TITLE).field(ES_QUESTIONS).field(ES_MATERIALS);
        return hiBuilder;
    }
```

###### 4.3 分页
####### 分页有两种，我这里使用的from，size.不过第一次查询获取总数时建议不拿数据只取counts，.setSize(0)：这样提高速度也能避免浪费性能
```	
	//查询建立
        SearchRequestBuilder responseBuilder = esClient
                .prepareSearch("interview").setTypes("iv");

        //第一次查询建立获取总条数分页使用
        SearchResponse myResponse = responseBuilder
                .setQuery(qb)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setSize(0)
                .setExplain(true).execute().actionGet();
        //获取总数
        long counts = myResponse.getHits().totalHits;
```
##### 4.4 建立查询
```
    //根据分页信息查询
        SearchResponse pageResponse = responseBuilder
                .setQuery(qb)
                .highlighter(hiBuilder)
                .setFrom((esRequestBean.getPage() - 1) * esRequestBean.getPageSize()).setSize(esRequestBean.getPageSize())
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setExplain(true).execute().actionGet();
        SearchHits pageHits = pageResponse.getHits();
        //循环装配bean
        for (SearchHit searchHit : pageHits) {
            Map source = searchHit.getSource();
            HighlightField titleHighlight = searchHit.getHighlightFields().get(ES_TITLE);
            HighlightField questionHighlight = searchHit.getHighlightFields().get(ES_QUESTIONS);
            HighlightField materialsHighlight = searchHit.getHighlightFields().get(ES_MATERIALS);
			//这里将取出的json数据转成bean再处理
            YourBean yourBean = (YourBean) JSONObject.toBean(JSONObject.fromObject(source), YourBean.class, classMap);
			.....
			.....
			}

```
####### 4.4.1 这里的classMap注意，当某字段信息是嵌套类型时 需要设好接受该字段的Map信息
```
	//对于嵌套数组类型，从Es中读取时需先反射定义好map
        Map<String, Class> classMap = new HashMap<String, Class>();
        classMap.put("es中字段", 接收该字段Bean.class);
       
```

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.json.JSONObject;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author li
 * @version Id: EsServiceImpl.java, v 0.1 2018年09月10日 17:44 li Exp $
 * 搜素引擎serviceImpl
 */
@Service
public class EsServiceImpl implements EsService {

    public static final String ES_TITLE = "title";

    public static final String ES_QUESTIONS = "questiones.question";

    public static final String ES_MATERIALS = "materials.materials";

    public static final String ES_CONTENT = "materialsContent";

    public static final String ES_OPTIONS = "options.value";

    /**
     * ElasticSearch client
     **/
    @Autowired
    public TransportClient esClient;
    /**
     * 系统参数表
     **/
    @Autowired
    private SystemParamMapper systemParamMapper;


    
    /**
     * 判断搜索词是否具有实际意义
     **/
    @Override
    public boolean EsContains(String query) {
        SystemParamDO questionTimesDO = systemParamMapper.selectByParamKey(ResourceTypeEnum.DEFAULT_TYPE_ES_SEARCH_INTERCEPT.getValue());
        String wordValue = questionTimesDO.getParamValue();
        List wordList = Arrays.asList(wordValue.split(","));
        return wordList.contains(query);
    }

    /**
     * 清洗HTML段列出匹配上的高亮词
     **/
    public List<String> highLight(String html) {
        //采用Jsoup解析高亮返回的Html
        Document doc = Jsoup.parse(html);
        //获取<mark>标签中的内容
        Elements elements = doc.select("mark");
        return elements.stream().map(Element::text).distinct().collect(Collectors.toList());
    }

    /**
     * 导入
     * 如果重新导入需去es控制台删除原有信息，否则会重复，不是覆盖
     **/
    @Override
    public boolean xcInput() {
        List<Long> lists = questionMapper.findByAllxcIds();
        //由于行测数量较多，使用bulk分批次提交，500条提交一次
        BulkRequestBuilder bulkRequest = esClient.prepareBulk();
        int count = 1;
        String mapJackson = null;
        for (Long id : lists) {
            YourBean xcBean = new YourBean();
            QuestionResponseBean qBean = questionService.findById(id);
            BeanUtils.copyProperties(qBean, xcBean);
            ObjectMapper mapper = new ObjectMapper();
            try {
                mapJackson = mapper.writeValueAsString(xcBean);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            //指定index和type
            bulkRequest.add(esClient.prepareIndex("yourIndex", "yourType").setSource(mapJackson, XContentType.JSON));
            if (count % 500 == 0) {
                bulkRequest.execute().actionGet();
                //此处新建一个bulkRequest，类似于重置效果。避免重复提交
                bulkRequest = esClient.prepareBulk();
            }
            count++;
        }
        if (count >= 500) {
            return true;
        } else
            return false;

    }


    /**
     * 查询
     **/
    @Override
    public PageResponseBean<EsQuestionResponseBean> SearchXc(EsPageRequestBean esRequestBean) {
        List<EsQuestionResponseBean> list = new ArrayList<>();
        //对于嵌套数组类型，从Es中读取时需先定义好map
        Map<String, Class> classMap = new HashMap<String, Class>();
        classMap.put("options", OptionsRequestBean.class);

        //多条件搜索设置
        MatchQueryBuilder mpq1 = QueryBuilders
                .matchQuery(ES_TITLE, esRequestBean.getQuery());
        MatchQueryBuilder mpq2 = QueryBuilders
                .matchQuery(ES_CONTENT, esRequestBean.getQuery());
        MatchQueryBuilder mpq3 = QueryBuilders
                .matchQuery(ES_QUESTIONS, esRequestBean.getQuery());
        //should可理解为or
        QueryBuilder qb = QueryBuilders.boolQuery()
                .should(mpq1)
                .should(mpq2)
                .should(mpq3);
        //关键字内容高亮设置
        HighlightBuilder hiBuilder = new HighlightBuilder();
        hiBuilder.preTags("<mark>").postTags("</mark>");
        //高亮匹配字段
        hiBuilder.field(ES_TITLE).field(ES_CONTENT).field(ES_OPTIONS);

        //建立连接
        SearchRequestBuilder responseBuilder = esClient
                .prepareSearch("yourIndex").setTypes("yourType");
        //第一次查询建立获取总条数分页使用
        SearchResponse myResponse = responseBuilder
                .setQuery(qb)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setSize(0)
                .setExplain(true).execute().actionGet();
        //获取总数
        long counts = myResponse.getHits().totalHits;
        PageResponseBean<EsQuestionResponseBean> page = new PageResponseBean<>(esRequestBean, (int) counts);
        if (page.getTotalElements() == 0) {
            return page;
        }
        //再根据分页信息查询
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
            HighlightField valueHighlight = searchHit.getHighlightFields().get(ES_OPTIONS);
            HighlightField materialsHighlight = searchHit.getHighlightFields().get(ES_CONTENT);
            EsQuestionResponseBean esquestionResponseBean = (EsQuestionResponseBean) JSONObject.toBean(JSONObject.fromObject(source), EsQuestionResponseBean.class, classMap);
            //给高亮字段赋值
            int highLight = 0;
            if (titleHighlight != null && highLight == 0) {
                esquestionResponseBean.setOptions(null);
                esquestionResponseBean.setMaterialsContent(null);
                highLight = 1;
                esquestionResponseBean.setTitleHighlight(this.highLight(titleHighlight.fragments()[0].toString()));
            }
            if (materialsHighlight != null && highLight == 0) {
                esquestionResponseBean.setOptions(null);
                esquestionResponseBean.setTitle(null);
                highLight = 1;
                esquestionResponseBean.setValueHighlight(this.highLight(materialsHighlight.fragments()[0].toString()));
            }
            if (valueHighlight != null && highLight == 0) {
                esquestionResponseBean.setTitle(null);
                esquestionResponseBean.setMaterialsContent(null);
                esquestionResponseBean.setValueHighlight(this.highLight(valueHighlight.fragments()[0].toString()));
                List<OptionsRequestBean> optionlist = esquestionResponseBean.getOptions();
                for (OptionsRequestBean optionsBean : optionlist) {
                    int bool = 0;
                    for (String word : esquestionResponseBean.getValueHighlight()) {
                        if (optionsBean.getValue().contains(word)) {
                            bool = 1;
                            break;
                        }
                    }
                    if (bool == 0) {
                        optionsBean.setValue(null);
                        optionsBean.setKey(null);
                    }
                }
            }
            //该题全站正确率
            esquestionResponseBean.setAverageAccuracy(questionMapper.findAccuracyById(esquestionResponseBean.getId()));
            list.add(esquestionResponseBean);
        }
        page.setContent(list);
        return page;
    }

   

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

    /**
     * 拼接高亮查询
     **/
    public HighlightBuilder getHb() {
        HighlightBuilder hiBuilder = new HighlightBuilder();
        hiBuilder.preTags("<mark>").postTags("</mark>");
        hiBuilder.field(ES_TITLE).field(ES_QUESTIONS).field(ES_MATERIALS);
        return hiBuilder;
    }

}

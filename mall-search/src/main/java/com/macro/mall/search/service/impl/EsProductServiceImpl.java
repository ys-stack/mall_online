package com.macro.mall.search.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.macro.mall.search.dao.EsProductDao;
import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.domain.EsProductRelatedInfo;
import com.macro.mall.search.domain.EsProductSearchParam;
import com.macro.mall.search.repository.EsProductRepository;
import com.macro.mall.search.service.EsProductService;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 搜索商品管理Service实现类
 * Created by macro on 2018/6/19.
 */
@Service
public class EsProductServiceImpl implements EsProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EsProductServiceImpl.class);
    @Autowired
    private EsProductDao productDao;
    @Autowired
    private EsProductRepository productRepository;
    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
    @Override
    public int importAll() {
        List<EsProduct> esProductList = productDao.getAllEsProductList(null);
        Iterable<EsProduct> esProductIterable = productRepository.saveAll(esProductList);
        Iterator<EsProduct> iterator = esProductIterable.iterator();
        int result = 0;
        while (iterator.hasNext()) {
            result++;
            iterator.next();
        }
        return result;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public EsProduct create(Long id) {
        EsProduct result = null;
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            result = productRepository.save(esProduct);
        }
        return result;
    }

    @Override
    public void delete(List<Long> ids) {
        if (!CollectionUtils.isEmpty(ids)) {
            List<EsProduct> esProductList = new ArrayList<>();
            for (Long id : ids) {
                EsProduct esProduct = new EsProduct();
                esProduct.setId(id);
                esProductList.add(esProduct);
            }
            productRepository.deleteAll(esProductList);
        }
    }

    @Override
    public Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        return productRepository.findByNameOrSubTitleOrKeywords(keyword, keyword, keyword, pageable);
    }

    @Override
    public Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize,Integer sort) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();
        //分页
        nativeSearchQueryBuilder.withPageable(pageable);
        //过滤
        if (brandId != null || productCategoryId != null) {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            if (brandId != null) {
                boolQueryBuilder.must(QueryBuilders.termQuery("brandId", brandId));
            }
            if (productCategoryId != null) {
                boolQueryBuilder.must(QueryBuilders.termQuery("productCategoryId", productCategoryId));
            }
            nativeSearchQueryBuilder.withFilter(boolQueryBuilder);
        }
        //搜索
        if (StrUtil.isEmpty(keyword)) {
            nativeSearchQueryBuilder.withQuery(QueryBuilders.matchAllQuery());
        } else {
            List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("name", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(10)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("subTitle", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(5)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("keywords", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(2)));
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
            filterFunctionBuilders.toArray(builders);
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(builders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .setMinScore(2);
            nativeSearchQueryBuilder.withQuery(functionScoreQueryBuilder);
        }
        //排序
        if(sort==1){
            //按新品从新到旧
            nativeSearchQueryBuilder.withSorts(SortBuilders.fieldSort("id").order(SortOrder.DESC));
        }else if(sort==2){
            //按销量从高到低
            nativeSearchQueryBuilder.withSorts(SortBuilders.fieldSort("sale").order(SortOrder.DESC));
        }else if(sort==3){
            //按价格从低到高
            nativeSearchQueryBuilder.withSorts(SortBuilders.fieldSort("price").order(SortOrder.ASC));
        }else if(sort==4){
            //按价格从高到低
            nativeSearchQueryBuilder.withSorts(SortBuilders.fieldSort("price").order(SortOrder.DESC));
        }else{
            //按相关度
            nativeSearchQueryBuilder.withSorts(SortBuilders.scoreSort().order(SortOrder.DESC));
        }
        nativeSearchQueryBuilder.withSorts(SortBuilders.scoreSort().order(SortOrder.DESC));
        NativeSearchQuery searchQuery = nativeSearchQueryBuilder.build();
        LOGGER.info("DSL:{}", searchQuery.getQuery().toString());
        SearchHits<EsProduct> searchHits = elasticsearchRestTemplate.search(searchQuery, EsProduct.class);
        if(searchHits.getTotalHits()<=0){
            return new PageImpl<>(ListUtil.empty(),pageable,0);
        }
        List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(searchProductList,pageable,searchHits.getTotalHits());
    }

    /**
     * 高级搜索：在关键词相关度基础上叠加品牌、分类、价格、库存和促销过滤。
     */
    @Override
    public Page<EsProduct> advancedSearch(EsProductSearchParam param, Integer pageNum, Integer pageSize) {
        if (param == null) {
            param = new EsProductSearchParam();
        }
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withPageable(pageable);
        builder.withQuery(buildKeywordScoreQuery(param.getKeyword()));
        BoolQueryBuilder filterBuilder = buildAdvancedFilter(param);
        if (filterBuilder.hasClauses()) {
            builder.withFilter(filterBuilder);
        }
        applyProductSort(builder, param.getSort());
        NativeSearchQuery searchQuery = builder.build();
        LOGGER.info("Advanced DSL:{}", searchQuery.getQuery().toString());
        return searchByQuery(searchQuery, pageable);
    }

    @Override
    public Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (esProductList.size() > 0) {
            EsProduct esProduct = esProductList.get(0);
            String keyword = esProduct.getName();
            Long brandId = esProduct.getBrandId();
            Long productCategoryId = esProduct.getProductCategoryId();
            //根据商品标题、品牌、分类进行搜索
            List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("name", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(8)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("subTitle", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(2)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("keywords", keyword),
                    ScoreFunctionBuilders.weightFactorFunction(2)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("brandId", brandId),
                    ScoreFunctionBuilders.weightFactorFunction(5)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("productCategoryId", productCategoryId),
                    ScoreFunctionBuilders.weightFactorFunction(3)));
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
            filterFunctionBuilders.toArray(builders);
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(builders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .setMinScore(2);
            //用于过滤掉相同的商品
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id",id));
            //构建查询条件
            NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
            builder.withQuery(functionScoreQueryBuilder);
            builder.withFilter(boolQueryBuilder);
            builder.withPageable(pageable);
            NativeSearchQuery searchQuery = builder.build();
            LOGGER.info("DSL:{}", searchQuery.getQuery().toString());
            SearchHits<EsProduct> searchHits = elasticsearchRestTemplate.search(searchQuery, EsProduct.class);
            if(searchHits.getTotalHits()<=0){
                return new PageImpl<>(ListUtil.empty(),pageable,0);
            }
            List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
            return new PageImpl<>(searchProductList,pageable,searchHits.getTotalHits());
        }
        return new PageImpl<>(ListUtil.empty());
    }

    /**
     * 增强推荐：先根据当前商品召回同类目、同品牌、文本相似和价格相近商品，再按销量、新品、推荐状态加权粗排。
     */
    @Override
    public Page<EsProduct> recommendAdvanced(Long id, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum, pageSize);
        List<EsProduct> esProductList = productDao.getAllEsProductList(id);
        if (CollectionUtils.isEmpty(esProductList)) {
            return new PageImpl<>(ListUtil.empty(), pageable, 0);
        }
        EsProduct seedProduct = esProductList.get(0);
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withPageable(pageable);
        builder.withQuery(buildRecommendScoreQuery(seedProduct));
        BoolQueryBuilder filterBuilder = QueryBuilders.boolQuery();
        filterBuilder.mustNot(QueryBuilders.termQuery("id", id));
        filterBuilder.filter(QueryBuilders.rangeQuery("stock").gt(0));
        builder.withFilter(filterBuilder);
        builder.withSorts(SortBuilders.scoreSort().order(SortOrder.DESC));
        builder.withSorts(SortBuilders.fieldSort("sale").order(SortOrder.DESC));
        NativeSearchQuery searchQuery = builder.build();
        LOGGER.info("Recommend advanced DSL:{}", searchQuery.getQuery().toString());
        return searchByQuery(searchQuery, pageable);
    }

    @Override
    public EsProductRelatedInfo searchRelatedInfo(String keyword) {
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        //搜索条件
        if(StrUtil.isEmpty(keyword)){
            builder.withQuery(QueryBuilders.matchAllQuery());
        }else{
            builder.withQuery(QueryBuilders.multiMatchQuery(keyword,"name","subTitle","keywords"));
        }
        //聚合搜索品牌名称
        builder.withAggregations(AggregationBuilders.terms("brandNames").field("brandName"));
        //聚合搜索分类名称
        builder.withAggregations(AggregationBuilders.terms("productCategoryNames").field("productCategoryName"));
        //聚合搜索商品属性，去除type=0的属性
        AbstractAggregationBuilder aggregationBuilder = AggregationBuilders.nested("allAttrValues","attrValueList")
                .subAggregation(AggregationBuilders.filter("productAttrs",QueryBuilders.termQuery("attrValueList.type",1))
                        .subAggregation(AggregationBuilders.terms("attrIds")
                                .field("attrValueList.productAttributeId")
                                .subAggregation(AggregationBuilders.terms("attrValues")
                                        .field("attrValueList.value"))
                                .subAggregation(AggregationBuilders.terms("attrNames")
                                        .field("attrValueList.name"))));
        builder.withAggregations(aggregationBuilder);
        NativeSearchQuery searchQuery = builder.build();
        SearchHits<EsProduct> searchHits = elasticsearchRestTemplate.search(searchQuery, EsProduct.class);
        return convertProductRelatedInfo(searchHits);
    }

    /**
     * 构建关键词相关度评分查询，商品名称权重最高，副标题次之，搜索关键词兜底。
     */
    private FunctionScoreQueryBuilder buildKeywordScoreQuery(String keyword) {
        if (StrUtil.isEmpty(keyword)) {
            return QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("recommandStatus", 1),
                                    ScoreFunctionBuilders.weightFactorFunction(1.2f)),
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("newStatus", 1),
                                    ScoreFunctionBuilders.weightFactorFunction(1.1f))
                    }).scoreMode(FunctionScoreQuery.ScoreMode.SUM);
        }
        List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("name", keyword),
                ScoreFunctionBuilders.weightFactorFunction(10)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("subTitle", keyword),
                ScoreFunctionBuilders.weightFactorFunction(5)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("keywords", keyword),
                ScoreFunctionBuilders.weightFactorFunction(3)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("recommandStatus", 1),
                ScoreFunctionBuilders.weightFactorFunction(1.5f)));
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
        filterFunctionBuilders.toArray(builders);
        return QueryBuilders.functionScoreQuery(builders)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .setMinScore(2);
    }

    /**
     * 构建高级搜索过滤条件，过滤条件不参与文本打分，能提升查询稳定性。
     */
    private BoolQueryBuilder buildAdvancedFilter(EsProductSearchParam param) {
        BoolQueryBuilder filterBuilder = QueryBuilders.boolQuery();
        if (param.getBrandId() != null) {
            filterBuilder.filter(QueryBuilders.termQuery("brandId", param.getBrandId()));
        }
        if (param.getProductCategoryId() != null) {
            filterBuilder.filter(QueryBuilders.termQuery("productCategoryId", param.getProductCategoryId()));
        }
        if (param.getMinPrice() != null || param.getMaxPrice() != null) {
            org.elasticsearch.index.query.RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");
            if (param.getMinPrice() != null) {
                rangeQueryBuilder.gte(param.getMinPrice());
            }
            if (param.getMaxPrice() != null) {
                rangeQueryBuilder.lte(param.getMaxPrice());
            }
            filterBuilder.filter(rangeQueryBuilder);
        }
        if (Boolean.TRUE.equals(param.getOnlyStock())) {
            filterBuilder.filter(QueryBuilders.rangeQuery("stock").gt(0));
        }
        if (param.getPromotionType() != null) {
            filterBuilder.filter(QueryBuilders.termQuery("promotionType", param.getPromotionType()));
        }
        return filterBuilder;
    }

    /**
     * 构建推荐评分查询，模拟“召回 + 多因子粗排”的推荐策略。
     */
    private FunctionScoreQueryBuilder buildRecommendScoreQuery(EsProduct seedProduct) {
        List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
        if (seedProduct.getProductCategoryId() != null) {
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("productCategoryId", seedProduct.getProductCategoryId()),
                    ScoreFunctionBuilders.weightFactorFunction(12)));
        }
        if (seedProduct.getBrandId() != null) {
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("brandId", seedProduct.getBrandId()),
                    ScoreFunctionBuilders.weightFactorFunction(5)));
        }
        if (StrUtil.isNotEmpty(seedProduct.getName())) {
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("name", seedProduct.getName()),
                    ScoreFunctionBuilders.weightFactorFunction(8)));
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("subTitle", seedProduct.getName()),
                    ScoreFunctionBuilders.weightFactorFunction(3)));
        }
        if (StrUtil.isNotEmpty(seedProduct.getKeywords())) {
            filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.matchQuery("keywords", seedProduct.getKeywords()),
                    ScoreFunctionBuilders.weightFactorFunction(4)));
        }
        addPriceBandFunction(filterFunctionBuilders, seedProduct.getPrice());
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("recommandStatus", 1),
                ScoreFunctionBuilders.weightFactorFunction(2)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.termQuery("newStatus", 1),
                ScoreFunctionBuilders.weightFactorFunction(1)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.rangeQuery("sale").gte(1000),
                ScoreFunctionBuilders.weightFactorFunction(4)));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(QueryBuilders.rangeQuery("sale").gte(100),
                ScoreFunctionBuilders.weightFactorFunction(2)));
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
        filterFunctionBuilders.toArray(builders);
        return QueryBuilders.functionScoreQuery(buildRecommendCandidateQuery(seedProduct), builders)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .setMinScore(2);
    }

    /**
     * 构建推荐候选集召回条件，先圈定相关商品，再进入多因子排序，避免全量商品被弱特征误召回。
     */
    private BoolQueryBuilder buildRecommendCandidateQuery(EsProduct seedProduct) {
        BoolQueryBuilder candidateQuery = QueryBuilders.boolQuery();
        if (seedProduct.getProductCategoryId() != null) {
            candidateQuery.should(QueryBuilders.termQuery("productCategoryId", seedProduct.getProductCategoryId()));
        }
        if (seedProduct.getBrandId() != null) {
            candidateQuery.should(QueryBuilders.termQuery("brandId", seedProduct.getBrandId()));
        }
        if (StrUtil.isNotEmpty(seedProduct.getName())) {
            candidateQuery.should(QueryBuilders.matchQuery("name", seedProduct.getName()));
            candidateQuery.should(QueryBuilders.matchQuery("subTitle", seedProduct.getName()));
        }
        if (StrUtil.isNotEmpty(seedProduct.getKeywords())) {
            candidateQuery.should(QueryBuilders.matchQuery("keywords", seedProduct.getKeywords()));
        }
        if (seedProduct.getPrice() != null && seedProduct.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal minPrice = seedProduct.getPrice().multiply(new BigDecimal("0.8"));
            BigDecimal maxPrice = seedProduct.getPrice().multiply(new BigDecimal("1.2"));
            candidateQuery.should(QueryBuilders.rangeQuery("price").gte(minPrice).lte(maxPrice));
        }
        if (!candidateQuery.hasClauses()) {
            candidateQuery.must(QueryBuilders.matchAllQuery());
        } else {
            candidateQuery.minimumShouldMatch(1);
        }
        return candidateQuery;
    }

    /**
     * 添加价格带相似度因子，避免推荐结果价格跨度过大影响转化。
     */
    private void addPriceBandFunction(List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal minPrice = price.multiply(new BigDecimal("0.8"));
        BigDecimal maxPrice = price.multiply(new BigDecimal("1.2"));
        filterFunctionBuilders.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                QueryBuilders.rangeQuery("price").gte(minPrice).lte(maxPrice),
                ScoreFunctionBuilders.weightFactorFunction(3)));
    }

    /**
     * 应用商品搜索排序规则，默认按相关度排序。
     */
    private void applyProductSort(NativeSearchQueryBuilder builder, Integer sort) {
        if (sort != null && sort == 1) {
            builder.withSorts(SortBuilders.fieldSort("id").order(SortOrder.DESC));
        } else if (sort != null && sort == 2) {
            builder.withSorts(SortBuilders.fieldSort("sale").order(SortOrder.DESC));
        } else if (sort != null && sort == 3) {
            builder.withSorts(SortBuilders.fieldSort("price").order(SortOrder.ASC));
        } else if (sort != null && sort == 4) {
            builder.withSorts(SortBuilders.fieldSort("price").order(SortOrder.DESC));
        }
        builder.withSorts(SortBuilders.scoreSort().order(SortOrder.DESC));
    }

    /**
     * 执行 ES 查询并统一转换分页结果。
     */
    private Page<EsProduct> searchByQuery(NativeSearchQuery searchQuery, Pageable pageable) {
        SearchHits<EsProduct> searchHits = elasticsearchRestTemplate.search(searchQuery, EsProduct.class);
        if (searchHits.getTotalHits() <= 0) {
            return new PageImpl<>(ListUtil.empty(), pageable, 0);
        }
        List<EsProduct> searchProductList = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
        return new PageImpl<>(searchProductList, pageable, searchHits.getTotalHits());
    }

    /**
     * 将返回结果转换为对象
     */
    private EsProductRelatedInfo convertProductRelatedInfo(SearchHits<EsProduct> response) {
        EsProductRelatedInfo productRelatedInfo = new EsProductRelatedInfo();
        Map<String, Aggregation> aggregationMap = ((Aggregations)response.getAggregations().aggregations()).asMap();
        //设置品牌
        Aggregation brandNames = aggregationMap.get("brandNames");
        List<String> brandNameList = new ArrayList<>();
        for(int i = 0; i<((Terms) brandNames).getBuckets().size(); i++){
            brandNameList.add(((Terms) brandNames).getBuckets().get(i).getKeyAsString());
        }
        productRelatedInfo.setBrandNames(brandNameList);
        //设置分类
        Aggregation productCategoryNames = aggregationMap.get("productCategoryNames");
        List<String> productCategoryNameList = new ArrayList<>();
        for(int i=0;i<((Terms) productCategoryNames).getBuckets().size();i++){
            productCategoryNameList.add(((Terms) productCategoryNames).getBuckets().get(i).getKeyAsString());
        }
        productRelatedInfo.setProductCategoryNames(productCategoryNameList);
        //设置参数
        Aggregation productAttrs = aggregationMap.get("allAttrValues");
        List<? extends Terms.Bucket> attrIds = ((ParsedLongTerms) ((ParsedFilter) ((ParsedNested) productAttrs).getAggregations().get("productAttrs")).getAggregations().get("attrIds")).getBuckets();
        List<EsProductRelatedInfo.ProductAttr> attrList = new ArrayList<>();
        for (Terms.Bucket attrId : attrIds) {
            EsProductRelatedInfo.ProductAttr attr = new EsProductRelatedInfo.ProductAttr();
            attr.setAttrId((Long) attrId.getKey());
            List<String> attrValueList = new ArrayList<>();
            List<? extends Terms.Bucket> attrValues = ((ParsedStringTerms) attrId.getAggregations().get("attrValues")).getBuckets();
            List<? extends Terms.Bucket> attrNames = ((ParsedStringTerms) attrId.getAggregations().get("attrNames")).getBuckets();
            for (Terms.Bucket attrValue : attrValues) {
                attrValueList.add(attrValue.getKeyAsString());
            }
            attr.setAttrValues(attrValueList);
            if(!CollectionUtils.isEmpty(attrNames)){
                String attrName = attrNames.get(0).getKeyAsString();
                attr.setAttrName(attrName);
            }
            attrList.add(attr);
        }
        productRelatedInfo.setProductAttrs(attrList);
        return productRelatedInfo;
    }
}

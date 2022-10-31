package com.warmer.web.controller;

import com.csvreader.CsvWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.warmer.base.enums.ReturnStatus;
import com.warmer.base.util.*;
import com.warmer.web.config.WebAppConfig;
import com.warmer.web.entity.KgDomain;
import com.warmer.web.entity.KgFeedBack;
import com.warmer.web.entity.KgNodeDetail;
import com.warmer.web.entity.KgNodeDetailFile;
import com.warmer.web.model.NodeItem;
import com.warmer.web.request.*;
import com.warmer.web.service.FeedBackService;
import com.warmer.web.service.KgGraphService;
import com.warmer.web.service.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping(value = "/")
public class KGManagerController extends BaseController {

    @Autowired
    private WebAppConfig config;
    @Autowired
    private KgGraphService kgGraphService;
    @Autowired
    private KnowledgeGraphService kgService;
    @Autowired
    FeedBackService feedBackService;

    @GetMapping("/")
    public String home() {
        return "kg/home";
    }

    @ResponseBody
    @RequestMapping(value = "/getGraph") // call db.labels
    public R<GraphPageRecord<KgDomain>> getGraph(@RequestBody GraphQuery queryItem) {
        GraphPageRecord<KgDomain> resultRecord = new GraphPageRecord<KgDomain>();
        try {
            PageHelper.startPage(queryItem.getPageIndex(), queryItem.getPageSize(), true);
            List<KgDomain> domainList = kgService.getDomainList(queryItem.getDomain(), queryItem.getType(),queryItem.getCommend());
            PageInfo<KgDomain> pageInfo = new PageInfo<KgDomain>(domainList);
            long total = pageInfo.getTotal();
            resultRecord.setPageIndex(queryItem.getPageIndex());
            resultRecord.setPageSize(queryItem.getPageSize());
            resultRecord.setTotalCount(new Long(total).intValue());
            resultRecord.setNodeList(pageInfo.getList());
            return R.success(resultRecord);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/getDomainGraph")
    public R<HashMap<String, Object>> getDomainGraph(@RequestBody GraphQuery query) {
        try {
            HashMap<String, Object> graphData = kgGraphService.getDomainGraph(query);
            return R.success(graphData);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }

    }

    @ResponseBody
    @RequestMapping(value = "/getCypherResult")
    public R<KgDomain> getCypherResult(String cypher) {
        try {
            HashMap<String, Object> graphData = Neo4jUtil.getGraphNodeAndShip(cypher);
            return R.success(graphData);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/getRelationNodeCount")
    public R<String> getRelationNodeCount(String domain, long nodeId) {
        try {
            long totalCount = 0;
            if (!StringUtil.isBlank(domain)) {
                totalCount = kgGraphService.getRelationNodeCount(domain, nodeId);
                return R.success(totalCount);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("操作失败");
    }

    @ResponseBody
    @RequestMapping(value = "/createDomain")
    public R<String> createDomain(String domain,Integer type) {
        try {
            if (!StringUtil.isBlank(domain)) {
                List<KgDomain> domainItem = kgService.getDomainByName(domain);
                if (domainItem.size() > 0) {
                    return R.create(ReturnStatus.Error, "领域已存在");
                } else {
                   int domainId= kgService.quickCreateDomain(domain,type);// 保存到mysql
                    kgGraphService.createDomain(domain);// 保存到图数据
                    return R.success(domainId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.success();
    }

    @ResponseBody
    @RequestMapping(value = "/getMoreRelationNode")
    public R<HashMap<String, Object>> getMoreRelationNode(String domain, String nodeId) {
        try {
            if (!StringUtil.isBlank(domain)) {
                HashMap<String, Object> graphModel = kgGraphService.getMoreRelationNode(domain, nodeId);
                if (graphModel != null) {
                    return R.success(graphModel);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("没有更多数据了");
    }

    @ResponseBody
    @RequestMapping(value = "/updateNodeName")
    public R<HashMap<String, Object>> updateNodeName(@RequestBody KgNodeItem request) {

        HashMap<String, Object> graphNodeList = new HashMap<String, Object>();
        try {
            if (!StringUtil.isBlank(request.getDomain())) {
                graphNodeList = kgGraphService.updateNodeName(request.getDomain(), request.getNodeId(), request.getNodeName());
                if (graphNodeList.size() > 0) {
                    return R.success(graphNodeList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("操作失败");
    }

    @ResponseBody
    @RequestMapping(value = "/updateCoordinateOfNode")
    public R<String> updateCoordinateOfNode(@RequestBody NodeCoordinateSubmitItem request) {
        try {
            String domain=request.getDomain();
            List<NodeCoordinateItem> nodes = request.getNodes();
            kgGraphService.batchUpdateGraphNodesCoordinate(domain,nodes);
            return R.success();
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/createNode")
    public R<HashMap<String, Object>> createNode(@RequestBody NodeItem entity) {
        HashMap<String, Object> graphNode = new HashMap<String, Object>();
        try {
            graphNode = kgGraphService.createNode(entity.getDomain(), entity);
            if (graphNode != null && graphNode.size() > 0) {
                return R.success(graphNode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("操作失败");

    }

    @ResponseBody
    @RequestMapping(value = "/batchCreateNode")
    public R<HashMap<String, Object>> batchCreateNode(@RequestBody BatchCreateNodeItem request) {

        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            String[] tNames=request.getTargetNames().split(",");
            rss = kgGraphService.batchCreateNode(request.getDomain(), request.getSourceName(), request.getRelation(),tNames);
            return R.success(rss);
        } catch (Exception e) {
            e.printStackTrace();

            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/batchCreateChildNode")
    public R<HashMap<String, Object>> batchCreateChildNode(@RequestBody BatchCreateNodeItem request) {

        HashMap<String, Object> rss = new HashMap<String, Object>();
        try {
            String[] tNames=request.getTargetNames().split(",");
            rss = kgGraphService.batchCreateChildNode(request.getDomain(), request.getSourceId(), request.getEntityType(), tNames, request.getRelation());
            return R.success(rss);
        } catch (Exception e) {
            e.printStackTrace();

            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/batchCreateSameNode")
    public R<List<HashMap<String, Object>>> batchCreateSameNode(@RequestBody BatchCreateNodeItem request) {
        List<HashMap<String, Object>> rss = new ArrayList<HashMap<String, Object>>();
        try {
            rss = kgGraphService.batchCreateSameNode(request.getDomain(), request.getEntityType(), request.getSourceNames());
            return R.success(rss);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/createLink")
    public R<HashMap<String, Object>> createLink(@RequestBody CreateLinkItem request) {
        try {
            HashMap<String, Object> cypherResult = kgGraphService.createLink(request.getDomain(), request.getSourceId(), request.getTargetId(), request.getShip());
            return R.success(cypherResult);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/updateLink")
    public R<HashMap<String, Object>> updateLink(String domain, long shipId, String shipName) {
        try {
            HashMap<String, Object> cypherResult = kgGraphService.updateLink(domain, shipId, shipName);
            return R.success(cypherResult);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }

    }

    @ResponseBody
    @RequestMapping(value = "/deleteNode")
    public R<List<HashMap<String, Object>>> deleteNode(String domain, long nodeId) {
        try {
            List<HashMap<String, Object>> rList = kgGraphService.deleteNode(domain, nodeId);
            return R.success(rList);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/deleteDomain")
    public R<List<HashMap<String, Object>>> deleteDomain(Integer domainId, String domain) {
        try {
            kgService.deleteDomain(domainId);
            kgGraphService.deleteKGDomain(domain);
            return R.success();
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/deleteLink")
    public R<HashMap<String, Object>> deleteLink(String domain, long shipId) {
        try {
            kgGraphService.deleteLink(domain, shipId);
            return R.success();

        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }

    }

    @ResponseBody
    @RequestMapping(value = "/importGraph")
    public R<String> importGraph(@RequestParam(value = "file", required = true)
                                 @Validated @NotNull(message = "请上传有效的excel的文件") @Pattern(regexp = "^(?:\\w+\\.xlsx|\\w+\\.xls)$",
                                    message = "请上传有效的excel的文件")
                                             MultipartFile file,
                                 HttpServletRequest request){
        try {
            String label = request.getParameter("domain");
            Integer type = Integer.parseInt(request.getParameter("type"));
            List<KgDomain> domainList = kgService.getDomainByName(label);
            int domainExist=0;
            if(domainList!=null&&domainList.size()>0){
                //导入已有图谱，更新图谱创建时间
                KgDomain domainItem = domainList.get(0);
                domainItem.setModifyTime(DateUtil.getDateNow());
                kgService.updateDomain(domainItem);
                domainExist=1;
            }else{
                label = StringUtil.isBlank(label) ? UuidUtil.getUUID() : label;
                kgService.quickCreateDomain(label,type);// 三元组
            }
            if (type.equals(1)) {//三元组导入
                kgGraphService.importBySyz(file, request, label,domainExist);
            } else {
                kgGraphService.importByCategory(file, request, label);
            }
            return R.success("操作成功");
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return R.error("处理失败");
        }
    }

    @ResponseBody
    @RequestMapping(value = "/exportGraph")
    public Map<String,Object> exportGraph(HttpServletRequest request) {
        Map<String,Object> res=new HashMap<>();
        String label = request.getParameter("domain");
        String filePath = config.getLocation();
        String fileName = UUID.randomUUID() + ".csv";
        String fileUrl = filePath + fileName;
        String cypher = String.format(
                "MATCH (n:`%s`) -[r]->(m:`%s`) return n.name as source,m.name as target,r.name as relation", label, label);
        List<HashMap<String, Object>> list = Neo4jUtil.getGraphTable(cypher);
        if(list.size()==0){
            res.put("code", -1);
            res.put("message", "该领域没有任何有关系的实体!");
            return res;
        }
        try {
            FileUtil.createFile(fileUrl);
            CsvWriter csvWriter = new CsvWriter(fileUrl, ',', StandardCharsets.UTF_8);
            String[] header = {"source", "target", "relation"};
            csvWriter.writeRecord(header);
            for (HashMap<String, Object> hashMap : list) {
                int colSize = hashMap.size();
                String[] cntArr = new String[colSize];
                cntArr[0] = hashMap.get("source").toString().replace("\"", "");
                cntArr[1] = hashMap.get("target").toString().replace("\"", "");
                cntArr[2] = hashMap.get("relation").toString().replace("\"", "");
                try {
                    csvWriter.writeRecord(cntArr);
                } catch (IOException e) {
                    log.error("CSVUtil->createFile: 文件输出异常" + e.getMessage());
                }
            }
            csvWriter.close();
            String serverUrl = request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
            String csvUrl = "/file/download/" + fileName;

            res.put("code", 200);
            res.put("fileName", csvUrl);
            res.put("message", "success!");
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;

    }

    @ResponseBody
    @RequestMapping(value = "/getNodeImage")
    public R<List<Map<String, Object>>> getNodeImagelist(int domainId, int nodeId) {
        try {
            List<KgNodeDetailFile> images = kgService.getNodeImageList(domainId, nodeId);
            return R.success(images);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/getNodeContent")
    public R<Map<String, Object>> getNodeContent(int domainId, int nodeId) {
        try {
            List<KgNodeDetail> contents = kgService.getNodeContent(domainId, nodeId);
            if (contents != null && contents.size() > 0) {
                return R.success(contents.get(0));
            }
            return R.success(new ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }

    }

    @ResponseBody
    @RequestMapping(value = "/getNodeDetail")
    public R<Map<String, Object>> getNodeDetail(int domainId, int nodeId) {
        try {
            Map<String, Object> res = new HashMap<String, Object>();
            res.put("content", "");
            res.put("imageList", new String[]{});
            List<KgNodeDetail> contents = kgService.getNodeContent(domainId, nodeId);
            if (contents != null && contents.size() > 0) {
                res.replace("content", contents.get(0).getContent());
            }
            List<KgNodeDetailFile> images = kgService.getNodeImageList(domainId, nodeId);
            if (images != null && images.size() > 0) {
                res.replace("imageList", images);
            }
            return R.success(res);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }
    @ResponseBody
    @RequestMapping(value = "/feedBack")
    public R<Map<String, Object>> feedBack(KgFeedBack submitItem) {
        try {
            feedBackService.insert(submitItem);
            return R.success();
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
    }
    @ResponseBody
    @RequestMapping(value = "/saveNodeImage")
    public R<String> saveNodeImage(@RequestBody Map<String, Object> params) {
        try {
            int domainId = (int) params.get("domainId");
            String nodeId = params.get("nodeId").toString();
            String imagePath = params.get("imagePath").toString();
            List<KgDomain> domainList = kgService.getDomainById(domainId);
            if (domainList != null && domainList.size() > 0) {
                String domainName = domainList.get(0).getName();
                if (StringUtil.isNotBlank(imagePath)) {
                    List<Map<String, Object>> submitItemList = new ArrayList<Map<String, Object>>();
                    Map<String, Object> sb = new HashMap<String, Object>();
                    sb.put("file", imagePath);
                    sb.put("imageType", 0);
                    sb.put("domainId", domainId);
                    sb.put("nodeId", nodeId);
                    sb.put("status", 1);
                    sb.put("createUser", "tc");
                    sb.put("createTime", DateUtil.getDateNow());
                    submitItemList.add(sb);
                    kgService.deleteNodeImage(domainId,Integer.parseInt(nodeId));
                    kgService.saveNodeImage(submitItemList);
                    // 更新到图数据库,表明该节点有附件,加个标识,0=没有,1=有
                    kgGraphService.updateNodeImg(domainName, Long.parseLong(nodeId), imagePath);
                    return R.success("操作成功");
                } else {
                    kgService.deleteNodeImage(domainId,Integer.parseInt(nodeId));
                    kgGraphService.removeNodeImg(domainName, Long.parseLong(nodeId));
                    return R.success("操作成功");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("操作失败");
    }

    @ResponseBody
    @RequestMapping(value = "/saveNodeContent")
    public R<String> saveNodeContent(@RequestBody Map<String, Object> params) {
        try {
            String username = "tc";
            int domainId = (int) params.get("domainId");
            String nodeId = params.get("nodeId").toString();
            String content = params.get("content").toString();
            List<KgDomain> domainList = kgService.getDomainById(domainId);
            if (domainList != null && domainList.size() > 0) {
                String domainName = domainList.get(0).getName();
                // 检查是否存在
                List<KgNodeDetail> items = kgService.getNodeContent(domainId, Integer.parseInt(nodeId));
                if (items != null && items.size() > 0) {
                    KgNodeDetail oldItem = items.get(0);
                    Map<String, Object> item = new HashMap<String, Object>();
                    item.put("domainId", oldItem.getDomainId());
                    item.put("nodeId", oldItem.getNodeId());
                    item.put("content", content);
                    item.put("modifyUser", username);
                    item.put("modifyTime", DateUtil.getDateNow());
                    kgService.updateNodeContent(item);
                    return R.success("更新成功");
                } else {
                    Map<String, Object> sb = new HashMap<String, Object>();
                    sb.put("content", content);
                    sb.put("domainId", domainId);
                    sb.put("nodeId", nodeId);
                    sb.put("status", 1);
                    sb.put("createUser", username);
                    sb.put("createTime", DateUtil.getDateNow());
                    if (sb.size() > 0) {
                        kgService.saveNodeContent(sb);
                        return R.success("保存成功");
                    }
                }
                // 更新到图数据库,表明该节点有附件,加个标识,0=没有,1=有
                kgGraphService.updateNodeFileStatus(domainName, Long.parseLong(nodeId), 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return R.error(e.getMessage());
        }
        return R.error("操作失败");
    }

}

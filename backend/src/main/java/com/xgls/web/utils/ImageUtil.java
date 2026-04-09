package com.xgls.web.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.xgls.web.base.CodeMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImageUtil {
    private static final String XPATH_EXPRESSION = "//image[box[@label='ignore'] | mask[@label='ignore']]";

    public static void processImage(String dir, Node imageNode) throws IOException {
        String fileName = imageNode.getAttributes().getNamedItem("name").getNodeValue();
        File imageFile = Path.of(dir, CodeMap.DIR_TRAIN_IMAGES, fileName).toFile();
        BufferedImage image = ImageIO.read(imageFile);
        NodeList chileNodeList = imageNode.getChildNodes();

        // 遍历所有 box 元素
        for (int j = 0; j < chileNodeList.getLength(); j++) {
            Node node = chileNodeList.item(j);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = node.getNodeName();
                if ("box".equals(tagName) || "mask".equals(tagName)) {
                    NamedNodeMap attributes = node.getAttributes();
                    Node labelNode = attributes.getNamedItem("label");
                    if (labelNode != null && labelNode.getNodeValue().equals("ignore")) {
                        int startX, startY, endX, endY;
                        switch (tagName) {
                            case "box":
                                int xtl = (int) Math
                                        .round(Double.parseDouble(attributes.getNamedItem("xtl").getNodeValue()));
                                int ytl = (int) Math
                                        .round(Double.parseDouble(attributes.getNamedItem("ytl").getNodeValue()));
                                int xbr = (int) Math
                                        .round(Double.parseDouble(attributes.getNamedItem("xbr").getNodeValue()));
                                int ybr = (int) Math
                                        .round(Double.parseDouble(attributes.getNamedItem("ybr").getNodeValue()));
                                if (xtl < xbr) {
                                    startX = xtl;
                                    endX = xbr;
                                } else {
                                    startX = xbr;
                                    endX = xtl;
                                }
                                if (ytl < ybr) {
                                    startY = ytl;
                                    endY = ybr;
                                } else {
                                    startY = ybr;
                                    endY = ytl;
                                }
                                break;
                            case "mask":
                                int left = Integer.parseInt(attributes.getNamedItem("left").getNodeValue());
                                int top = Integer.parseInt(attributes.getNamedItem("top").getNodeValue());
                                int width = Integer.parseInt(attributes.getNamedItem("width").getNodeValue());
                                int height = Integer.parseInt(attributes.getNamedItem("height").getNodeValue());
                                startX = left;
                                startY = top;
                                endX = startX + width-1;
                                endY = startY + height-1;
                                break;
                            default:
                                continue;
                        }

                        makeRegionTransparent(image, startX, startY, endX, endY);
                    }
                }
            }
        }
        String suff = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        ImageIO.write(image, suff, imageFile); // 保存修改后的图片
    }

    private static void makeRegionTransparent(BufferedImage image, int startX, int startY, int endX, int endY) {
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                image.setRGB(x, y, 0); // RGB值为0表示黑色
            }
        }
    }

    public static void processIgor(String dir) {
        log.info("process ignor label:{}", dir);
        try {
            Path xmlPath = Path.of(dir, "annotations.xml");
            File xmlFile = xmlPath.toFile();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);

            // 创建 XPath 实例
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();
            // 执行 XPath 查询
            XPathExpression xPathExpression = xPath.compile(XPATH_EXPRESSION);
            NodeList nodeList = (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                // 转换单个图片
                Node imageNode = nodeList.item(i);
                processImage(dir, imageNode);
            }
            log.info("process ignor label finish image_num:{}", nodeList.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

package com.niaobulashi.controller;

import com.niaobulashi.common.dto.ResponseCode;
import com.niaobulashi.dao.SysFileInfoDao;
import com.niaobulashi.model.SysFileInfo;
import com.niaobulashi.properties.GlobalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @program: spring-boot-learning
 * @description: 文件
 * @author: hulang  hulang6666@qq.com
 * @create: 2019-07-19 15:28
 */
@Controller
public class FileController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 默认大小 50M
     */
    public static final long DEFAULT_MAX_SIZE = 50 * 1024 * 1024;

    @Autowired
    private SysFileInfoDao sysFileInfoDao;

    @Autowired
    private GlobalProperties globalProperties;

    /**
     * 文件上传页面
     *
     * @return
     */
    @GetMapping("/")
    public String updatePage() {
        return "file";
    }

    /**
     * 单文件上传
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ResponseBody
    private ResponseCode upload(@RequestParam("file") MultipartFile file) throws Exception {
        // 获取文件在服务器上的存储位置
        String serverPath = globalProperties.getServerPath();

        // 获取允许上传的文件扩展名
        String extension = globalProperties.getExtension();

        File filePath = new File(serverPath);
        logger.info("文件保存的路径为：" + filePath);
        if (!filePath.exists() && !filePath.isDirectory()) {
            logger.info("目录不存在，则创建目录：" + filePath);
            filePath.mkdir();
        }

        // 判断文件是否为空
        if (file.isEmpty()) {
            return ResponseCode.error("文件为空");
        }
        //判断文件是否为空文件
        if (file.getSize() <= 0) {
            return ResponseCode.error("文件大小为空，上传失败");
        }

        // 判断文件大小不能大于50M
        if (DEFAULT_MAX_SIZE != -1 && file.getSize() > DEFAULT_MAX_SIZE) {
            return ResponseCode.error("上传的文件不能大于50M");
        }

        // 获取文件名
        String fileName = file.getOriginalFilename();
        // 获取文件扩展名
        String fileExtension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        // 判断文件扩展名是否正确
        if (!extension.contains(fileExtension)) {
            return ResponseCode.error("文件扩展名不正确");
        }

        SysFileInfo sysFileInfo = new SysFileInfo();
        // 重新生成的文件名
        String saveFileName = System.currentTimeMillis() + fileExtension;
        // 在指定目录下创建该文件
        File targetFile = new File(filePath, saveFileName);

        logger.info("将文件保存到指定目录");
        try {
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }

        // 保存数据
        sysFileInfo.setFileName(fileName);
        sysFileInfo.setFilePath(serverPath + "/" + saveFileName);
        sysFileInfo.setFileSize(file.getSize());

        logger.info("新增文件数据");
        // 新增文件数据
        sysFileInfoDao.save(sysFileInfo);
        return ResponseCode.success("上传成功");
    }


    /**
     * 下载
     *
     * @param fileId
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/download")
    @ResponseBody
    public ResponseCode downloadFile(@RequestParam("fileId") Integer fileId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        logger.info("文件ID为：" + fileId);
        // 判断传入参数是否非空
        if (fileId == null) {
            return ResponseCode.error("请求参数不能为空");
        }
        // 根据fileId查询文件表
        Optional<SysFileInfo> sysFileInfo = sysFileInfoDao.findById(fileId);
        if (Objects.isNull(sysFileInfo.get())) {
            return ResponseCode.error("下载的文件不存在");
        }
        // 获取文件全路径
        File file = new File(sysFileInfo.get().getFilePath());
        String fileName = sysFileInfo.get().getFileName();
        // 判断是否存在磁盘中
        if (file.exists()) {
            // 设置强制下载不打开
            response.setContentType("application/force-download");
            // 设置文件名
            response.addHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(fileName, "UTF-8"));
            byte[] buffer = new byte[1024];
            FileInputStream fis = null;
            BufferedInputStream bis = null;
            try {
                fis = new FileInputStream(file);
                bis = new BufferedInputStream(fis);
                OutputStream os = response.getOutputStream();
                int i = bis.read(buffer);
                while (i != -1) {
                    os.write(buffer, 0, i);
                    i = bis.read(buffer);
                }
                return ResponseCode.success("下载成功");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            return ResponseCode.error("数据库查询存在，本地磁盘不存在文件");
        }
        return ResponseCode.success("下载失败");
    }


    /**
     * 批量文件上传
     *
     * @param files
     * @return
     * @throws Exception
     */
    @PostMapping("/batchUpload")
    @ResponseBody
    public ResponseCode batchUpload(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files == null) {
            return ResponseCode.error("参数为空");
        }
        for (MultipartFile multipartFile : files) {
            upload(multipartFile);
        }
        return ResponseCode.success("批量上传成功");
    }


    @PostMapping("/batchDownload")
    @ResponseBody
    public ResponseCode downloadFiles(@RequestParam("fileIds") List<Integer> fileIdList, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 判断传入参数是否非空
        if (CollectionUtils.isEmpty(fileIdList)) {
            return ResponseCode.error("请求参数不能为空");
        }
        // 根据fileId查询文件表
        List<SysFileInfo> sysFileInfo = sysFileInfoDao.findAllById(fileIdList);
        if (CollectionUtils.isEmpty(sysFileInfo)) {
            return ResponseCode.error("下载的文件不存在");
        }
        // 判断将下载的文件列表中是否有不存在的
        if (fileIdList.size() != sysFileInfo.size()) {
            return ResponseCode.error("申请下载的文件中存在服务器缺少的文件");
        }
        // 获取文件在服务器上的存储位置
        String serverPath = globalProperties.getServerPath();
        //创建压缩文件需要的空的zip包
        String zipName = "temp.zip";

        File filePath = new File(serverPath);
        logger.info("压缩包生成的路径为：" + filePath);
        if (!filePath.exists() && !filePath.isDirectory()) {
            logger.info("目录不存在，则创建目录：" + filePath);
            filePath.mkdir();
        }
        //组合成完整的文件路径
        String targetPath = serverPath + File.separator + zipName;

        logger.info("完整的文件路径：" + targetPath);
        //压缩文件
        File zipFile = new File(targetPath);
        if (!zipFile.exists()) {
            zipFile.createNewFile();
        }

        // 创建压缩临时文件
        this.generateZipFile(sysFileInfo, zipFile);

        // 设置强制下载不打开
        response.setContentType("application/force-download");
        // 设置文件名
        response.addHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode("我是ZIP.zip", "UTF-8"));
        OutputStream os = response.getOutputStream();
        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        try {
            FileInputStream fis = new FileInputStream(targetPath);
            bis = new BufferedInputStream(fis);
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                i = bis.read(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //删除服务器本地产生的临时压缩文件
            zipFile.delete();
            os.close();
        }

        return ResponseCode.success("下载失败");
    }

    /**
     * 压缩文件
     *
     * @param sysFileInfoList
     * @param zipFile
     * @throws IOException
     */
    private void generateZipFile(List<SysFileInfo> sysFileInfoList, File zipFile) throws IOException {
        //初始化zip文件输出流
        ZipOutputStream zos = null;
        try {
            //创建文件输出流
            FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
            //实例化zip文件输出流
            zos = new ZipOutputStream(fileOutputStream);
            //循环读取文件路径集合，获取每一个文件的路径
            for (SysFileInfo sysFileInfo : sysFileInfoList) {
                //根据文件路径创建文件
                File inputFile = new File(sysFileInfo.getFilePath());
                //判断文件是否存在
                if (inputFile.exists()) {
                    //判断是否属于文件
                    if (inputFile.isFile()) {
                        //创建输入流读取文件
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile));
                        //将文件写入zip内，即将文件进行打包
                        zos.putNextEntry(new ZipEntry(sysFileInfo.getFileName()));
                        //写入文件的方法，同上
                        int size = 0;
                        //设置读取数据缓存大小
                        byte[] buffer = new byte[1024];
                        while ((size = bis.read(buffer)) > 0) {
                            zos.write(buffer, 0, size);
                        }
                        //关闭输入输出流
                        zos.closeEntry();
                        bis.close();
                    } else {
                        // 报不是文件
                    }
                } else {
                    logger.error("数据库查询存在，本地磁盘不存在文件");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
    }

}


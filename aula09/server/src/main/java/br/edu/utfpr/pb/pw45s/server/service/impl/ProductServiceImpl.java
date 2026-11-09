package br.edu.utfpr.pb.pw45s.server.service.impl;

import br.edu.utfpr.pb.pw45s.server.storage.payload.FileResponse;
import br.edu.utfpr.pb.pw45s.server.storage.service.StorageService;
import br.edu.utfpr.pb.pw45s.server.storage.util.FileTypeUtils;
import br.edu.utfpr.pb.pw45s.server.model.Product;
import br.edu.utfpr.pb.pw45s.server.repository.ProductRepository;
import br.edu.utfpr.pb.pw45s.server.service.IProductService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

@Service
@Slf4j
public class ProductServiceImpl extends CrudServiceImpl<Product, Long>
        implements IProductService {

    private final ProductRepository productRepository;

    private final StorageService storageService;

    public ProductServiceImpl(ProductRepository productRepository, StorageService storageService) {
        this.productRepository = productRepository;
        this.storageService = storageService;
    }

    @Override
    protected JpaRepository<Product, Long> getRepository() {
        return productRepository;
    }

    @Override
    public Product save(Product entity, MultipartFile file) {
        String fileType = FileTypeUtils.getFileType(file);
        if (fileType != null) {
            FileResponse fileResponse = storageService.putObject(file, "commons",
                    fileType);
            entity.setImageName(fileResponse.getFilename());
            entity.setContentType(fileResponse.getContentType());
        } else {
            log.atWarn()
                    .addKeyValue("event", "product.image.unsupported")
                    .addKeyValue("file.name", file.getOriginalFilename())
                    .log("Unsupported image type for file {}, product will be saved without image",
                            file.getOriginalFilename());
        }
        Product saved = super.save(entity);
        log.atInfo()
                .addKeyValue("event", "product.created")
                .addKeyValue("resource.id", saved.getId())
                .addKeyValue("file.name", saved.getImageName())
                .log("Product {} saved with image {}", saved.getId(), saved.getImageName());
        return saved;
    }

    @Override
    public void downloadFile(Long id, HttpServletResponse response) {
        InputStream in = null;
        try {
            Product product = this.findById(id);
            if (product == null || product.getImageName() == null) {
                log.atWarn()
                        .addKeyValue("event", "product.image.not_found")
                        .addKeyValue("resource.id", id)
                        .log("Product {} not found or without image", id);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            log.atInfo()
                    .addKeyValue("event", "product.image.download")
                    .addKeyValue("resource.id", id)
                    .addKeyValue("file.name", product.getImageName())
                    .log("Downloading image {} of product {}", product.getImageName(), id);
            in = storageService.downloadObject("commons", product.getImageName());
            response.setHeader("Content-Disposition", "attachment;filename="
                    + URLEncoder.encode(product.getImageName(), "UTF-8"));
            response.setCharacterEncoding("UTF-8");
            // Remove bytes from InputStream Copied to the OutputStream .
            in.transferTo(response.getOutputStream());
        } catch (IOException e) {
            log.atError()
                    .addKeyValue("event", "product.image.download_failed")
                    .addKeyValue("resource.id", id)
                    .setCause(e)
                    .log("Error downloading image of product {}", id);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
}

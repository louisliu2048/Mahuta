package net.consensys.mahuta.api.http.endpoint;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import net.consensys.mahuta.core.Mahuta;
import net.consensys.mahuta.core.domain.Metadata;
import net.consensys.mahuta.core.domain.MetadataAndPayload;
import net.consensys.mahuta.core.domain.common.Page;
import net.consensys.mahuta.core.domain.common.PageRequest;
import net.consensys.mahuta.core.domain.common.PageRequest.SortDirection;
import net.consensys.mahuta.core.domain.searching.Query;
import net.consensys.mahuta.core.exception.TimeoutException;
import net.consensys.mahuta.core.utils.lamba.Throwing;

@RestController
@Slf4j
public class QueryController {

    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String DEFAULT_PAGE_NO = "0";

    private final ObjectMapper mapper;
    private final Mahuta mahuta;

    @Autowired
    public QueryController(Mahuta mahuta) {
        this.mahuta = mahuta;
        this.mapper = new ObjectMapper();
    }

    /**
     * Get content by hash
     *
     * @param index Index name
     * @param hash  File Unique Identifier
     * @return File content
     * @throws TimeoutException
     */
    @GetMapping(value = "${mahuta.api-spec.v1.query.fetch}")
    public @ResponseBody ResponseEntity<byte[]> getFile(@PathVariable(value = "hash") @NotNull String hash,
            @RequestParam(value = "index", required = false) String indexName, HttpServletResponse response) {

        // Find and get content by hash
        MetadataAndPayload metadataAndPayload = mahuta.getByHash(indexName, hash);

        // Attach content-type to the header
        response.setContentType(Optional.ofNullable(metadataAndPayload.getMetadata().getContentType())
                .orElseGet(() -> "application/octet-stream1"));
        log.info("response.getContentType()={}", response.getContentType());

        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(Optional.ofNullable(metadataAndPayload.getMetadata().getContentType())
                .map(MediaType::valueOf).orElseGet(() -> MediaType.APPLICATION_OCTET_STREAM));

        return new ResponseEntity<>(
                ((ByteArrayOutputStream) metadataAndPayload.getPayload()).toByteArray(),
                httpHeaders, 
                HttpStatus.OK);
    }

    /**
     * Search contents By HTTP POST request
     *
     * @param index         Index name
     * @param pageNo        Page no [optional - default 1]
     * @param pageSize      Page size [optional - default 20]
     * @param sortAttribute Sorting attribute [optional]
     * @param sortDirection Sorting direction [optional - default ASC]
     * @param query         Query
     * @return List of result
     */
    @PostMapping(value = "${mahuta.api-spec.v1.query.search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Page<Metadata> searchContentsByPost(
            @RequestParam(value = "index", required = false) String indexName,
            @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE_NO) int pageNo,
            @RequestParam(value = "size", required = false, defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "sort", required = false) Optional<String> sortAttribute,
            @RequestParam(value = "dir", required = false, defaultValue = "ASC") SortDirection sortDirection,
            @RequestBody Query query) {

        return executeSearch(indexName, pageNo, pageSize, sortAttribute, sortDirection, query);
    }

    /**
     * Search contents By HTTP GET request
     *
     * @param index         Index name
     * @param pageNo        Page no [optional - default 1]
     * @param pageSize      Page size [optional - default 20]
     * @param sortAttribute Sorting attribute [optional]
     * @param sortDirection Sorting direction [optional - default ASC]
     * @param queryStr      Query
     * @return List of result
     */
    @GetMapping(value = "${mahuta.api-spec.v1.query.search}", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Page<Metadata> searchContentsByGet(
            @RequestParam(value = "index", required = false) String indexName,
            @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE_NO) int pageNo,
            @RequestParam(value = "size", required = false, defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "sort", required = false) Optional<String> sortAttribute,
            @RequestParam(value = "dir", required = false, defaultValue = "ASC") SortDirection sortDirection,
            @RequestParam(value = "query", required = false) Optional<String> queryStr) {

        Query query = queryStr.map(Throwing.rethrowFunc(q -> this.mapper.readValue(q, Query.class))).orElse(null);

        return executeSearch(indexName, pageNo, pageSize, sortAttribute, sortDirection, query);
    }

    /**
     * execute search (common to searchContentsByGet and searchContentsByPost)
     * 
     * @param index         Index name
     * @param pageNo        Page no [optional - default 1]
     * @param pageSize      Page size [optional - default 20]
     * @param sortAttribute Sorting attribute [optional]
     * @param sortDirection Sorting direction [optional - default ASC]
     * @param queryStr      Query
     * @return List of result
     */
    private Page<Metadata> executeSearch(String indexName, int pageNo, int pageSize, Optional<String> sortAttribute,
            SortDirection sortDirection, Query query) {

        PageRequest pageRequest = sortAttribute
                .map(s -> PageRequest.of(pageNo, pageSize, sortAttribute.get(), sortDirection))
                .orElse(PageRequest.of(pageNo, pageSize));

        return mahuta.search(indexName, query, pageRequest);
    }

}
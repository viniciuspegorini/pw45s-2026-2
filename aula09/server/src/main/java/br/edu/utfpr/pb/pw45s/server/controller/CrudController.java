package br.edu.utfpr.pb.pw45s.server.controller;

import br.edu.utfpr.pb.pw45s.server.service.ICrudService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

// T = class type, D = dto type, ID = attribute related to primary key type
public abstract class CrudController<T, D, ID extends Serializable> {

	// Logger da classe concreta (ProductController, CategoryController...), facilitando o filtro no Loki
	private final Logger log = LoggerFactory.getLogger(getClass());

	// Nome do recurso (product, category...) utilizado como atributo dos logs
	private final String resource = getClass().getSimpleName().replace("Controller", "").toLowerCase();

	protected abstract ICrudService<T, ID> getService();

	protected abstract D toDto(T entity);

	protected abstract T toEntity(D dto);

	private D convertToDto(T entity) {
		return toDto(entity);
	}

	private T convertToEntity(D entityDto) {
		return toEntity(entityDto);
	}

	@GetMapping //http://ip-api:port/request-mapping
	public ResponseEntity<List<D>> findAll() {
		List<D> list = getService().findAll().stream().map(this::convertToDto).collect(Collectors.toList());
		log.atInfo()
				.addKeyValue("event", resource + ".list")
				.addKeyValue("resource", resource)
				.addKeyValue("result.size", list.size())
				.log("Listing {}: {} records found", resource, list.size());
		return ResponseEntity.ok(list);
	}

	@GetMapping("page")  //http://ip-api:port/request-mapping/page?page=1&size=5
	public ResponseEntity<Page<D>> findAll(@RequestParam int page,
	                                       @RequestParam int size,
	                                       @RequestParam(required = false) String order,
	                                       @RequestParam(required = false) Boolean asc) {
		PageRequest pageRequest = PageRequest.of(page, size);
		if (order != null && asc != null) {
			pageRequest = PageRequest.of(page, size, asc ? Sort.Direction.ASC : Sort.Direction.DESC, order);
		}
		Page<D> result = getService().findAll(pageRequest).map(this::convertToDto);
		log.atInfo()
				.addKeyValue("event", resource + ".page")
				.addKeyValue("resource", resource)
				.addKeyValue("page", page)
				.addKeyValue("size", size)
				.addKeyValue("result.total", result.getTotalElements())
				.log("Listing {} page {} (size {}): {} records in total", resource, page, size, result.getTotalElements());
		return ResponseEntity.ok(result);
	}

	@GetMapping("{id}")
	public ResponseEntity<D> findOne(@PathVariable ID id) {
		T entity = getService().findById(id);
		if (entity != null) {
			log.atInfo()
					.addKeyValue("event", resource + ".find")
					.addKeyValue("resource", resource)
					.addKeyValue("resource.id", id)
					.log("{} with id {} found", resource, id);
			return ResponseEntity.ok(convertToDto(entity));
		} else {
			log.atWarn()
					.addKeyValue("event", resource + ".not_found")
					.addKeyValue("resource", resource)
					.addKeyValue("resource.id", id)
					.log("{} with id {} not found", resource, id);
			return ResponseEntity.noContent().build();
		}
	}

	@PostMapping
	public ResponseEntity<D> create(@RequestBody @Valid D entity) {
		D saved = convertToDto(getService().save(convertToEntity(entity)));
		log.atInfo()
				.addKeyValue("event", resource + ".created")
				.addKeyValue("resource", resource)
				.log("{} created", resource);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@PutMapping("{id}")
	public ResponseEntity<D> update(@PathVariable ID id, @RequestBody @Valid D entity) {
		D saved = convertToDto(getService().save(convertToEntity(entity)));
		log.atInfo()
				.addKeyValue("event", resource + ".updated")
				.addKeyValue("resource", resource)
				.addKeyValue("resource.id", id)
				.log("{} with id {} updated", resource, id);
		return ResponseEntity.status(HttpStatus.OK).body(saved);
	}

	@GetMapping("exists/{id}")
	public ResponseEntity<Boolean> exists(@PathVariable ID id) {
		return ResponseEntity.ok(getService().exists(id));
	}

	@GetMapping("count")
	public ResponseEntity<Long> count() {
		return ResponseEntity.ok(getService().count());
	}

	@DeleteMapping("{id}")
	public ResponseEntity<Void> delete(@PathVariable ID id) {
		getService().deleteById(id);
		log.atInfo()
				.addKeyValue("event", resource + ".deleted")
				.addKeyValue("resource", resource)
				.addKeyValue("resource.id", id)
				.log("{} with id {} deleted", resource, id);
		return ResponseEntity.noContent().build();
	}

}

package com.mcqbuddy.attempt;

import com.mcqbuddy.attempt.service.MarkingSchemeService;
import com.mcqbuddy.bean.entity.markingscheme.MarkingScheme;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/attempt-api/marking-schemes")
public class MarkingSchemeRestAPIController {

    private final MarkingSchemeService markingSchemeService;

    public MarkingSchemeRestAPIController(MarkingSchemeService markingSchemeService) {
        this.markingSchemeService = markingSchemeService;
    }

    @PostMapping(value = "")
    public ResponseEntity<?> create(@RequestBody MarkingScheme ms) {
        return ResponseEntity.ok(markingSchemeService.create(ms));
    }

    @GetMapping(value = "")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(markingSchemeService.list());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<?> get(@PathVariable int id) {
        return ResponseEntity.ok(markingSchemeService.get(id));
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody MarkingScheme ms) {
        return ResponseEntity.ok(markingSchemeService.update(id, ms));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        markingSchemeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

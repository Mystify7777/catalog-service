package com.nhcarrigan.catalogservice.dto;

/** Describes a validation or business error encountered while importing one CSV row. */
public record ProductImportError(int row, String reason) {}
package com.devdevgo.jobs.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "job_listings", createIndex = true)
public class JobListingDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String company;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String redirectUrl;

    @Field(type = FieldType.Keyword)
    private String createdAt;

    @Field(type = FieldType.Long)
    private Long createdAtEpochSeconds;

    @Field(type = FieldType.Keyword)
    private String contractType;

    @Field(type = FieldType.Keyword)
    private String contractTime;

    @Field(type = FieldType.Double)
    private Double salaryMin;

    @Field(type = FieldType.Double)
    private Double salaryMax;

    @Field(type = FieldType.Boolean)
    private boolean salaryPredicted;

    @Field(type = FieldType.Double)
    private Double latitude;

    @Field(type = FieldType.Double)
    private Double longitude;

    @Field(type = FieldType.Text)
    private String category;

    @Field(type = FieldType.Text)
    private String categoryTag;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Keyword)
    private String searchProfile;

    @Field(type = FieldType.Text)
    private String searchedWhat;

    @Field(type = FieldType.Text)
    private String searchedWhere;

    @Field(type = FieldType.Keyword)
    private String fetchedAt;

    @Field(type = FieldType.Long)
    private Long fetchedAtEpochSeconds;

    @Field(type = FieldType.Text)
    private String normalizedText;

    @Field(type = FieldType.Text)
    private String searchText;

    @Field(type = FieldType.Keyword)
    private List<String> tags;
}

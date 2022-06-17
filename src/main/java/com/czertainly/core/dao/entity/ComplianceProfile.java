package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.compliance.*;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "compliance_profile")
public class ComplianceProfile extends Audited implements Serializable, DtoMapper<ComplianceProfileDto> {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "compliance_profile_seq")
    @SequenceGenerator(name = "compliance_profile_seq", sequenceName = "compliance_profile_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @JsonBackReference
    @OneToMany(mappedBy = "complianceProfile")
    private Set<ComplianceProfileRule> complianceRules = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "compliance_group")
    private Set<ComplianceGroup> complianceGroups;

    @JsonBackReference
    @OneToMany(mappedBy = "complianceProfile")
    private Set<RaProfile> raProfiles = new HashSet<>();

    @Override
    public ComplianceProfileDto mapToDto(){
        ComplianceProfileDto complianceProfileDto = new ComplianceProfileDto();
        complianceProfileDto.setName(name);
        complianceProfileDto.setUuid(uuid);
        complianceProfileDto.setRaProfiles(raProfiles.stream().map(RaProfile::mapToDtoReduced).collect(Collectors.toList()));
        Map<String, List<ComplianceRulesDto>> rules = new HashMap<>();

        for(ComplianceProfileRule complianceRule: complianceRules){
            ComplianceRule rul = complianceRule.getComplianceRule();
            String ruleKey = rul.getConnector().getUuid() + ":" + rul.getConnector().getName() + ":" + rul.getKind();
            rules.computeIfAbsent(ruleKey, k -> new ArrayList<>()).add(complianceRule.mapToDto());
        }
        List<ComplianceConnectorAndRulesDto> rulesDtos = new ArrayList<>();
        for(Map.Entry<String, List<ComplianceRulesDto>> entry : rules.entrySet()){
            ComplianceConnectorAndRulesDto complianceConnectorAndRulesDto = new ComplianceConnectorAndRulesDto();
            String[] nameSplits = entry.getKey().split(":");
            complianceConnectorAndRulesDto.setConnectorName(nameSplits[1]);
            complianceConnectorAndRulesDto.setKind(nameSplits[2]);
            complianceConnectorAndRulesDto.setConnectorUuid(nameSplits[0]);
            complianceConnectorAndRulesDto.setRules(entry.getValue());
            rulesDtos.add(complianceConnectorAndRulesDto);
        }
        complianceProfileDto.setRules(rulesDtos);
        return complianceProfileDto;
    }

    /**
     *
     * @return ComplianceProfilesListDto with the response for listing operation
     */
    public ComplianceProfilesListDto ListMapToDTO(){
        ComplianceProfilesListDto complianceProfileDto = new ComplianceProfilesListDto();
        complianceProfileDto.setName(name);
        complianceProfileDto.setUuid(uuid);

        Map<String, Integer> providerSummary = new HashMap<>();
        for(ComplianceProfileRule complianceRule : complianceRules){
            String connectorName = complianceRule.getComplianceRule().getConnector().getName();
            if(providerSummary.containsKey(connectorName)){
                providerSummary.put(connectorName, providerSummary.get(connectorName) + 1);
            } else {
                providerSummary.put(connectorName, 1);
            }
        }

        List<ComplianceProviderSummaryDto> complianceProviderSummaryDtoList = new ArrayList<>();
        for(String connectorName : providerSummary.keySet()){
            ComplianceProviderSummaryDto complianceProviderSummaryDto = new ComplianceProviderSummaryDto();
            complianceProviderSummaryDto.setConnectorName(connectorName);
            complianceProviderSummaryDto.setNumberOfRules(providerSummary.get(connectorName));
            complianceProviderSummaryDtoList.add(complianceProviderSummaryDto);
        }
        complianceProfileDto.setRules(complianceProviderSummaryDtoList);
        return complianceProfileDto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("name", name)
                .append("description", description)
                .append("complianceRules", complianceRules)
                .append("raProfiles", raProfiles)
                .toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<ComplianceProfileRule> getComplianceRules() {
        return complianceRules;
    }

    public void setComplianceRules(Set<ComplianceProfileRule> complianceRules) {
        this.complianceRules = complianceRules;
    }

    public Set<RaProfile> getRaProfiles() {
        return raProfiles;
    }

    public void setRaProfiles(Set<RaProfile> raProfiles) {
        this.raProfiles = raProfiles;
    }

    public Set<ComplianceGroup> getComplianceGroups() {
        return complianceGroups;
    }

    public void setComplianceGroups(Set<ComplianceGroup> complianceGroups) {
        this.complianceGroups = complianceGroups;
    }
}

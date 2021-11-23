package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "connector_2_function_group")
public class Connector2FunctionGroup {
	
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "connector_2_function_group_seq")
	@SequenceGenerator(name = "connector_2_function_group_seq", sequenceName = "connector_2_function_group_id_seq", allocationSize = 1)
	private Long id;
	
	@ManyToOne
    @JoinColumn(name = "connector_id", nullable = false)
    @JsonIgnore
    private Connector connector;
	
	@ManyToOne
    @JoinColumn(name = "function_group_id", nullable = false)
	private FunctionGroup functionGroup;
	
	@Column(name = "kinds")
	private String kinds;
	
	public Connector getConnector() {
		return connector;
	}
	public void setConnector(Connector connector) {
		this.connector = connector;
	}
	public FunctionGroup getFunctionGroup() {
		return functionGroup;
	}
	public void setFunctionGroup(FunctionGroup functionGroup) {
		this.functionGroup = functionGroup;
	}
	public String getKinds() {
		return kinds;
	}
	public void setKinds(String kinds) {
		this.kinds = kinds;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Connector2FunctionGroup that = (Connector2FunctionGroup) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

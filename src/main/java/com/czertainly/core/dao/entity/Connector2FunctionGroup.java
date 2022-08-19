package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "connector_2_function_group")
public class Connector2FunctionGroup {
	
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "connector_2_function_group_seq")
	@SequenceGenerator(name = "connector_2_function_group_seq", sequenceName = "connector_2_function_group_id_seq", allocationSize = 1)
	private Long id;
	
	@ManyToOne
    @JoinColumn(name = "connector_uuid", nullable = false)
    @JsonIgnore
    private Connector connector;

	@Column(name = "connector_uuid")
	private String connectorUuid;

	public String getConnectorUuid() {
		return connectorUuid;
	}

	public void setConnectorUuid(String connectorUuid) {
		this.connectorUuid = connectorUuid;
	}

	public String getFunctionGroupUuid() {
		return functionGroupUuid;
	}

	public void setFunctionGroupUuid(String functionGroupUuid) {
		this.functionGroupUuid = functionGroupUuid;
	}

	@ManyToOne
    @JoinColumn(name = "function_group_uuid", nullable = false)
	private FunctionGroup functionGroup;

	@Column(name = "function_group_uuid")
	private String functionGroupUuid;
	
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

package com.ldbc.impls.workloads.ldbc.snb.sparql.bi;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.ldbc.driver.DbException;
import com.ldbc.driver.workloads.ldbc.snb.bi.LdbcSnbBiQuery16ExpertsInSocialCircle;
import com.ldbc.impls.workloads.ldbc.snb.bi.BiQueryStore;
import com.ldbc.impls.workloads.ldbc.snb.util.Converter;
import com.ldbc.impls.workloads.ldbc.snb.util.SparqlConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SparqlBiQueryStore extends BiQueryStore {

	public SparqlBiQueryStore(String path) throws DbException {
		super(path, "bi-", ".sparql");
	}

	@Override
	protected Converter getConverter() {
		return new SparqlConverter();
	}

	@Override
	protected String prepare(QueryType queryType, Map<String, String> paramaterSubstitutions) {
		String query = queries.get(queryType);
		for (String parameter : queryType.parameters) {
			query = query.replace("$" + parameter, paramaterSubstitutions.get(parameter));
		}
		return query;
	}

	/*
		for example, with a minPathDistance of 1 and a maxPathDistance of 3, we get the following snippet:

		{
                ?rootPerson snvoc:knows ?know1 .
                ?know1 snvoc:hasPerson ?personId .
		} UNION {
                ?rootPerson snvoc:knows ?know1 .
				?know1 snvoc:hasPerson ?person1 .
				?person1 snvoc:knows ?know2 .
				?know2 snvoc:hasPerson ?personId .
				FILTER( ?know1 != ?know2 )
		} UNION {
                ?rootPerson snvoc:knows ?know1 .
				?know1 snvoc:hasPerson ?person1 .
				?person1 snvoc:knows ?know2 .
				?know2 snvoc:hasPerson ?person2 .
				?person2 snvoc:knows ?know3 .
				?know2 snvoc:hasPerson ?personId .
				FILTER( ?know1 != ?know2 )
				FILTER( ?know2 != ?know3 )
				FILTER( ?know1 != ?know3 )
		}
	 */
	public String getQuery16(LdbcSnbBiQuery16ExpertsInSocialCircle operation) {
		List<String> knowsTrailFragments = new ArrayList<>();

		for (int k = operation.minPathDistance(); k <= operation.maxPathDistance(); k++) {
			String s = "{\n";

			List<String> currentNodes = new ArrayList<>();
			currentNodes.add("rootPerson");
			for (int i = 1; i < k - 1; i++) {
				currentNodes.add(String.format("person%d", i));
			}
			currentNodes.add("personId");

			for (int i = 1; i < k; i++) {
				s += String.format("                ?%s snvoc:knows ?know%d .\n", currentNodes.get(i - 1), i);
				s += String.format("                ?know%d snvoc:hasPerson ?%s .\n", i, currentNodes.get(i));
			}

			for (int i = 1; i < k; i++) {
				for (int j = i + 1; j < k; j++) {
					s += String.format(
							"                FILTER ( ?know%d != ?know%d )\n",
							i, j
					);
				}
			}

			s += "            }";
			knowsTrailFragments.add(s);
		}

		Joiner joiner = Joiner.on(" UNION ");
		String knowsTrails = joiner.join(knowsTrailFragments);

		return prepare(QueryType.Query16, new ImmutableMap.Builder<String, String>()
				.put("personId", Long.toString(operation.personId()))
				.put("country", getConverter().convertString(operation.country()))
				.put("tagClass", getConverter().convertString(operation.tagClass()))
				.put("minPathDistance", knowsTrails)
				.put("maxPathDistance", "").build());
	}

}

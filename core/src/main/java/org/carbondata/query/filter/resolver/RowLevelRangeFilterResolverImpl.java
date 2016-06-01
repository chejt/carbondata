/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.carbondata.query.filter.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;

import org.carbondata.core.carbon.AbsoluteTableIdentifier;
import org.carbondata.core.carbon.datastore.block.SegmentProperties;
import org.carbondata.core.carbon.metadata.schema.table.column.CarbonMeasure;
import org.carbondata.core.constants.CarbonCommonConstants;
import org.carbondata.core.util.ByteUtil;
import org.carbondata.query.carbon.executor.exception.QueryExecutionException;
import org.carbondata.query.carbonfilterinterface.FilterExecuterType;
import org.carbondata.query.expression.ColumnExpression;
import org.carbondata.query.expression.Expression;
import org.carbondata.query.expression.ExpressionResult;
import org.carbondata.query.expression.conditional.BinaryConditionalExpression;
import org.carbondata.query.expression.logical.BinaryLogicalExpression;
import org.carbondata.query.filter.resolver.resolverinfo.DimColumnResolvedFilterInfo;
import org.carbondata.query.filter.resolver.resolverinfo.MeasureColumnResolvedFilterInfo;
import org.carbondata.query.filters.measurefilter.util.FilterUtil;
import org.carbondata.query.schema.metadata.DimColumnFilterInfo;

public class RowLevelRangeFilterResolverImpl extends ConditionalFilterResolverImpl {

  /**
   *
   */
  private static final long serialVersionUID = 6629319265336666789L;

  private List<DimColumnResolvedFilterInfo> dimColEvaluatorInfoList;
  private List<MeasureColumnResolvedFilterInfo> msrColEvalutorInfoList;
  private AbsoluteTableIdentifier tableIdentifier;

  public RowLevelRangeFilterResolverImpl(Expression exp, boolean isExpressionResolve,
      boolean isIncludeFilter, AbsoluteTableIdentifier tableIdentifier) {
    super(exp, isExpressionResolve, isIncludeFilter);
    dimColEvaluatorInfoList =
        new ArrayList<DimColumnResolvedFilterInfo>(CarbonCommonConstants.DEFAULT_COLLECTION_SIZE);
    msrColEvalutorInfoList = new ArrayList<MeasureColumnResolvedFilterInfo>(
        CarbonCommonConstants.DEFAULT_COLLECTION_SIZE);
    this.tableIdentifier = tableIdentifier;
  }

  /**
   * This method will return the filter values which is present in the range leve
   * conditional expressions.
   *
   * @return
   */
  public byte[][] getFilterRangeValues() {
    List<byte[]> filterValuesList = new ArrayList<byte[]>();
    if (null != dimColEvaluatorInfoList.get(0).getFilterValues()) {
      filterValuesList =
          dimColEvaluatorInfoList.get(0).getFilterValues().getNoDictionaryFilterValuesList();
      return filterValuesList.toArray((new byte[filterValuesList.size()][]));
    }
    return filterValuesList.toArray((new byte[filterValuesList.size()][]));

  }

  /**
   * method will get the start key based on the filter surrogates
   *
   * @return start IndexKey
   */
  public void getStartKey(SegmentProperties segmentProperties, long[] startKey,
      SortedMap<Integer, byte[]> noDictStartKeys) {
    if (null == dimColEvaluatorInfoList.get(0).getStarIndexKey()) {
      FilterUtil
          .getStartKeyForNoDictionaryDimension(dimColEvaluatorInfoList.get(0), segmentProperties,
              noDictStartKeys);
    }
  }

  /**
   * method will get the start key based on the filter surrogates
   *
   * @return end IndexKey
   */
  @Override public void getEndKey(SegmentProperties segmentProperties,
      AbsoluteTableIdentifier absoluteTableIdentifier, long[] endKeys,
      SortedMap<Integer, byte[]> noDicEndKeys) {
    if (null == dimColEvaluatorInfoList.get(0).getEndIndexKey()) {
      try {
        FilterUtil.getEndKey(dimColEvaluatorInfoList.get(0).getDimensionResolvedFilterInstance(),
            absoluteTableIdentifier, endKeys, segmentProperties);
        FilterUtil
            .getEndKeyForNoDictionaryDimension(dimColEvaluatorInfoList.get(0), segmentProperties,
                noDicEndKeys);
      } catch (QueryExecutionException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private List<byte[]> getNoDictionaryRangeValues() {
    List<ExpressionResult> listOfExpressionResults = new ArrayList<ExpressionResult>(20);
    if (this.getFilterExpression() instanceof BinaryConditionalExpression) {
      listOfExpressionResults =
          ((BinaryConditionalExpression) this.getFilterExpression()).getLiterals();
    }
    List<byte[]> filterValuesList = new ArrayList<byte[]>(20);
    for (ExpressionResult result : listOfExpressionResults) {
      if (result.getString() == null) {
        filterValuesList.add(CarbonCommonConstants.MEMBER_DEFAULT_VAL.getBytes());
        continue;
      }
      filterValuesList.add(result.getString().getBytes());
    }
    Comparator<byte[]> filterNoDictValueComaparator = new Comparator<byte[]>() {
      @Override public int compare(byte[] filterMember1, byte[] filterMember2) {
        return ByteUtil.UnsafeComparer.INSTANCE.compareTo(filterMember1, filterMember2);
      }

    };
    Collections.sort(filterValuesList, filterNoDictValueComaparator);
    return filterValuesList;
  }

  /**
   * Method which will resolve the filter expression by converting the filter
   * member to its assigned dictionary values.
   */
  public void resolve(AbsoluteTableIdentifier absoluteTableIdentifier) {
    DimColumnResolvedFilterInfo dimColumnEvaluatorInfo = null;
    MeasureColumnResolvedFilterInfo msrColumnEvalutorInfo = null;
    int index = 0;
    if (exp instanceof BinaryLogicalExpression) {
      BinaryLogicalExpression conditionalExpression = (BinaryLogicalExpression) exp;
      List<ColumnExpression> columnList = conditionalExpression.getColumnList();
      for (ColumnExpression columnExpression : columnList) {
        if (columnExpression.isDimension()) {
          dimColumnEvaluatorInfo = new DimColumnResolvedFilterInfo();
          DimColumnFilterInfo filterInfo = new DimColumnFilterInfo();
          dimColumnEvaluatorInfo.setColumnIndex(columnExpression.getCarbonColumn().getOrdinal());
          //dimColumnEvaluatorInfo.se
          dimColumnEvaluatorInfo.setRowIndex(index++);
          dimColumnEvaluatorInfo.setDimension(columnExpression.getDimension());
          dimColumnEvaluatorInfo.setDimensionExistsInCurrentSilce(false);
          filterInfo.setFilterListForNoDictionaryCols(getNoDictionaryRangeValues());
          filterInfo.setIncludeFilter(isIncludeFilter);
          dimColumnEvaluatorInfo.setFilterValues(filterInfo);
          dimColumnEvaluatorInfo
              .addDimensionResolvedFilterInstance(columnExpression.getDimension(), filterInfo);
          dimColEvaluatorInfoList.add(dimColumnEvaluatorInfo);
        } else {
          msrColumnEvalutorInfo = new MeasureColumnResolvedFilterInfo();
          msrColumnEvalutorInfo.setRowIndex(index++);
          msrColumnEvalutorInfo.setAggregator(
              ((CarbonMeasure) columnExpression.getCarbonColumn()).getAggregateFunction());
          msrColumnEvalutorInfo
              .setColumnIndex(((CarbonMeasure) columnExpression.getCarbonColumn()).getOrdinal());
          msrColumnEvalutorInfo.setType(columnExpression.getCarbonColumn().getDataType());
          msrColEvalutorInfoList.add(msrColumnEvalutorInfo);
        }
      }
    }
  }

  /**
   * Method will return the DimColumnResolvedFilterInfo instance which consists
   * the mapping of the respective dimension and its surrogates involved in
   * filter expression.
   *
   * @return DimColumnResolvedFilterInfo
   */
  public List<DimColumnResolvedFilterInfo> getDimColEvaluatorInfoList() {
    return dimColEvaluatorInfoList;
  }

  /**
   * Method will return the DimColumnResolvedFilterInfo instance which containts
   * measure level details.
   *
   * @return MeasureColumnResolvedFilterInfo
   */
  public List<MeasureColumnResolvedFilterInfo> getMsrColEvalutorInfoList() {
    return msrColEvalutorInfoList;
  }

  public AbsoluteTableIdentifier getTableIdentifier() {
    return tableIdentifier;
  }

  public Expression getFilterExpression() {
    return this.exp;
  }

  /**
   * This method will provide the executer type to the callee inorder to identify
   * the executer type for the filter resolution, Row level filter executer is a
   * special executer since it get all the rows of the specified filter dimension
   * and will be send to the spark for processing
   */
  public FilterExecuterType getFilterExecuterType() {
    switch (exp.getFilterExpressionType()) {
      case GREATERTHAN:
        return FilterExecuterType.ROWLEVEL_GREATERTHAN;
      case GREATERTHAN_EQUALTO:
        return FilterExecuterType.ROWLEVEL_GREATERTHAN_EQUALTO;
      case LESSTHAN:
        return FilterExecuterType.ROWLEVEL_LESSTHAN;
      case LESSTHAN_EQUALTO:
        return FilterExecuterType.ROWLEVEL_LESSTHAN_EQUALTO;

      default:
        return FilterExecuterType.ROWLEVEL;
    }
  }
}

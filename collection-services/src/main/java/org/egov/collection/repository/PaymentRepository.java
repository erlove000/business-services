package org.egov.collection.repository;

import static java.util.Collections.reverseOrder;
import static org.egov.collection.config.CollectionServiceConstants.KEY_FILESTOREID;
import static org.egov.collection.config.CollectionServiceConstants.KEY_ID;
import static org.egov.collection.repository.querybuilder.PaymentQueryBuilder.*;

import java.util.*;
import java.util.stream.Collectors;

import org.egov.collection.model.Payment;
import org.egov.collection.model.PaymentDetail;
import org.egov.collection.model.PaymentSearchCriteria;
import org.egov.collection.repository.querybuilder.PaymentQueryBuilder;
import org.egov.collection.repository.rowmapper.BillRowMapper;
import org.egov.collection.repository.rowmapper.PaymentRowMapper;
import org.egov.collection.web.contract.Bill;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class PaymentRepository {


    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private PaymentQueryBuilder paymentQueryBuilder;

    private PaymentRowMapper paymentRowMapper;
    
    private BillRowMapper billRowMapper;

    @Autowired
    public PaymentRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, PaymentQueryBuilder paymentQueryBuilder, 
    		PaymentRowMapper paymentRowMapper, BillRowMapper billRowMapper) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.paymentQueryBuilder = paymentQueryBuilder;
        this.paymentRowMapper = paymentRowMapper;
        this.billRowMapper = billRowMapper;
    }




    @Transactional
    public void savePayment(Payment payment){
        try {

            List<MapSqlParameterSource> paymentDetailSource = new ArrayList<>();
            List<MapSqlParameterSource> billSource = new ArrayList<>();
            List<MapSqlParameterSource> billDetailSource = new ArrayList<>();
            List<MapSqlParameterSource> billAccountDetailSource = new ArrayList<>();

            for (PaymentDetail paymentDetail : payment.getPaymentDetails()) {
                paymentDetailSource.add(getParametersForPaymentDetailCreate(payment.getId(), paymentDetail));
                billSource.add(getParamtersForBillCreate(paymentDetail.getBill()));
                paymentDetail.getBill().getBillDetails().forEach(billDetail -> {
                    billDetailSource.add(getParamtersForBillDetailCreate(billDetail));
                    billDetail.getBillAccountDetails().forEach(billAccountDetail -> {
                        billAccountDetailSource.add(getParametersForBillAccountDetailCreate(billAccountDetail));
                    });
                });

            }
            namedParameterJdbcTemplate.update(INSERT_PAYMENT_SQL, getParametersForPaymentCreate(payment));
            namedParameterJdbcTemplate.batchUpdate(INSERT_PAYMENTDETAIL_SQL, paymentDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(INSERT_BILL_SQL, billSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(INSERT_BILLDETAIL_SQL, billDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(INSERT_BILLACCOUNTDETAIL_SQL,  billAccountDetailSource.toArray(new MapSqlParameterSource[0]));

        }catch (Exception e){
            log.error("Failed to persist payment to database", e);
            throw new CustomException("PAYMENT_CREATION_FAILED", e.getMessage());
        }
    }


    public List<Payment> fetchPayments(PaymentSearchCriteria paymentSearchCriteria) {
        Map<String, Object> preparedStatementValues = new HashMap<>();

        List<String> ids = fetchPaymentIdsByCriteria(paymentSearchCriteria);

        if(CollectionUtils.isEmpty(ids))
            return new LinkedList<>();

        String query = paymentQueryBuilder.getPaymentSearchQuery(ids, preparedStatementValues);
        log.info("Query: " + query);
        log.info("preparedStatementValues: " + preparedStatementValues);
        List<Payment> payments = namedParameterJdbcTemplate.query(query, preparedStatementValues, paymentRowMapper);
        if (!CollectionUtils.isEmpty(payments)) {
            Set<String> billIds = new HashSet<>();
            for (Payment payment : payments) {
                billIds.addAll(payment.getPaymentDetails().stream().map(detail -> detail.getBillId()).collect(Collectors.toSet()));
            }
            Map<String, Bill> billMap = getBills(billIds);
            for (Payment payment : payments) {
                payment.getPaymentDetails().forEach(detail -> {
                    detail.setBill(billMap.get(detail.getBillId()));
                });
            }
            payments.sort(reverseOrder(Comparator.comparingLong(Payment::getTransactionDate)));
        }

        return payments;
    }

    public List<Payment> fetchPaymentsForPlainSearch(PaymentSearchCriteria paymentSearchCriteria) {
        Map<String, Object> preparedStatementValues = new HashMap<>();
        String query = paymentQueryBuilder.getPaymentSearchQueryForPlainSearch(paymentSearchCriteria, preparedStatementValues);
        log.info("Query: " + query);
        log.info("preparedStatementValues: " + preparedStatementValues);
        List<Payment> payments = namedParameterJdbcTemplate.query(query, preparedStatementValues, paymentRowMapper);
        if (!CollectionUtils.isEmpty(payments)) {
            Set<String> billIds = new HashSet<>();
            for (Payment payment : payments) {
                billIds.addAll(payment.getPaymentDetails().stream().map(detail -> detail.getBillId()).collect(Collectors.toSet()));
            }
            Map<String, Bill> billMap = getBills(billIds);
            for (Payment payment : payments) {
                payment.getPaymentDetails().forEach(detail -> {
                    detail.setBill(billMap.get(detail.getBillId()));
                });
            }
            payments.sort(reverseOrder(Comparator.comparingLong(Payment::getTransactionDate)));
        }

        return payments;
    }


    
    private Map<String, Bill> getBills(Set<String> ids){
    	Map<String, Bill> mapOfIdAndBills = new HashMap<>();
        Map<String, Object> preparedStatementValues = new HashMap<>();
        preparedStatementValues.put("id", ids);
        String query = paymentQueryBuilder.getBillQuery();
        List<Bill> bills = namedParameterJdbcTemplate.query(query, preparedStatementValues, billRowMapper);
        bills.forEach(bill -> {
        	mapOfIdAndBills.put(bill.getId(), bill);
        });
        
        return mapOfIdAndBills;

    }



    public void updateStatus(List<Payment> payments){
        List<MapSqlParameterSource> paymentSource = new ArrayList<>();
        List<MapSqlParameterSource> paymentDetailSource = new ArrayList<>();
        List<MapSqlParameterSource> billSource = new ArrayList<>();
        try {

            for(Payment payment : payments){
                paymentSource.add(getParametersForPaymentStatusUpdate(payment));
                for (PaymentDetail paymentDetail : payment.getPaymentDetails()) {
                    paymentDetailSource.add(getParametersForPaymentDetailStatusUpdate(paymentDetail));
                    billSource.add(getParamtersForBillStatusUpdate(paymentDetail.getBill()));
                }
            }

            namedParameterJdbcTemplate.batchUpdate(COPY_PAYMENT_SQL, paymentSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_PAYMENTDETAIL_SQL, paymentDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_BILL_SQL, billSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(STATUS_UPDATE_PAYMENT_SQL, paymentSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(STATUS_UPDATE_PAYMENTDETAIL_SQL, paymentDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(STATUS_UPDATE_BILL_SQL, billSource.toArray(new MapSqlParameterSource[0]));
        }
        catch(Exception e){
            log.error("Failed to persist cancel Receipt to database", e);
            throw new CustomException("CANCEL_RECEIPT_FAILED", "Unable to cancel Receipt");
        }
    }


    public void updatePayment(List<Payment> payments){
        List<MapSqlParameterSource> paymentSource = new ArrayList<>();
        List<MapSqlParameterSource> paymentDetailSource = new ArrayList<>();
        List<MapSqlParameterSource> billSource = new ArrayList<>();
        List<MapSqlParameterSource> billDetailSource = new ArrayList<>();

        try {

            for (Payment payment : payments) {
                paymentSource.add(getParametersForPaymentUpdate(payment));
                payment.getPaymentDetails().forEach(paymentDetail -> {
                    paymentDetailSource.add(getParametersForPaymentDetailUpdate(paymentDetail));
                    billSource.add(getParamtersForBillUpdate(paymentDetail.getBill()));

                    paymentDetail.getBill().getBillDetails().forEach(billDetail -> {
                        billDetailSource.add(getParamtersForBillDetailUpdate(billDetail));
                    });

                });
            }
            namedParameterJdbcTemplate.batchUpdate(UPDATE_PAYMENT_SQL, paymentSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(UPDATE_PAYMENTDETAIL_SQL, paymentDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(UPDATE_BILL_SQL, billSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(UPDATE_BILLDETAIL_SQL, billDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_PAYMENT_SQL, paymentSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_PAYMENTDETAIL_SQL, paymentDetailSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_BILL_SQL, billSource.toArray(new MapSqlParameterSource[0]));
            namedParameterJdbcTemplate.batchUpdate(COPY_BILLDETAIL_SQL, billDetailSource.toArray(new MapSqlParameterSource[0]));
        }catch (Exception e){
            log.error("Failed to update receipt to database", e);
            throw new CustomException("RECEIPT_UPDATION_FAILED", "Unable to update receipt");
        }
    }


    public void updateFileStoreId(List<Map<String,String>> idToFileStoreIdMaps){

        List<MapSqlParameterSource> fileStoreIdSource = new ArrayList<>();

        idToFileStoreIdMaps.forEach(map -> {
            MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();
            sqlParameterSource.addValue("id",map.get(KEY_ID));
            sqlParameterSource.addValue("filestoreid",map.get(KEY_FILESTOREID));
            fileStoreIdSource.add(sqlParameterSource);
        });

        namedParameterJdbcTemplate.batchUpdate(FILESTOREID_UPDATE_PAYMENT_SQL,fileStoreIdSource.toArray(new MapSqlParameterSource[0]));

    }
    
    public void updateFileStoreIdToNull(Payment payment){

     
      List<MapSqlParameterSource> fileStoreIdSource = new ArrayList<>();
	  
      MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();
      sqlParameterSource.addValue("id",payment.getId());
      fileStoreIdSource.add(sqlParameterSource);

      namedParameterJdbcTemplate.batchUpdate(FILESTOREID_UPDATE_NULL_PAYMENT_SQL,fileStoreIdSource.toArray(new MapSqlParameterSource[0]));

    }

    public List<String> fetchPaymentIds(PaymentSearchCriteria paymentSearchCriteria) {

    	StringBuilder query = new StringBuilder("SELECT id from egcl_payment ");
    	boolean whereCluaseApplied= false ;
    	boolean isTenantPresent= true ;
        Map<String, Object> preparedStatementValues = new HashMap<>();
        preparedStatementValues.put("offset", paymentSearchCriteria.getOffset());
        preparedStatementValues.put("limit", paymentSearchCriteria.getLimit());
        if(paymentSearchCriteria.getTenantId() != null && !paymentSearchCriteria.getTenantId().equals("pb")) {
            query.append(" WHERE tenantid=:tenantid ");
            preparedStatementValues.put("tenantid", paymentSearchCriteria.getTenantId());
            whereCluaseApplied=true;
        }else {
        	isTenantPresent = false;
		whereCluaseApplied=false;
        	query.append(" WHERE id in (select paymentid from egcl_paymentdetail WHERE createdtime between :fromDate and :toDate) ");
        	preparedStatementValues.put("fromDate", paymentSearchCriteria.getFromDate());
                preparedStatementValues.put("toDate", paymentSearchCriteria.getToDate());
        } 
        
        if(paymentSearchCriteria.getBusinessServices() != null && isTenantPresent && whereCluaseApplied) {
        	if(whereCluaseApplied) {
            	query.append(" AND id in (select paymentid from egcl_paymentdetail where tenantid=:tenantid AND businessservice=:businessservice) ");
                preparedStatementValues.put("tenantid", paymentSearchCriteria.getTenantId());
                preparedStatementValues.put("businessservice", paymentSearchCriteria.getBusinessServices());

        	}
        }
        
        if(paymentSearchCriteria.getBusinessService() != null && isTenantPresent && whereCluaseApplied) {
            log.info("In side the repo before query: " + paymentSearchCriteria.getBusinessService() );
           query.append(" AND id in (select paymentid from egcl_paymentdetail where tenantid=:tenantid AND businessservice=:businessservice) ");
            preparedStatementValues.put("tenantid", paymentSearchCriteria.getTenantId());
            preparedStatementValues.put("businessservice", paymentSearchCriteria.getBusinessService());
        }
        
        if(paymentSearchCriteria.getFromDate() != null && isTenantPresent && whereCluaseApplied) {
          log.info("In side the repo before query: " + paymentSearchCriteria.getBusinessService() );
           query.append("  AND  createdtime between :fromDate and :toDate");
           preparedStatementValues.put("fromDate", paymentSearchCriteria.getFromDate());
           preparedStatementValues.put("toDate", paymentSearchCriteria.getToDate());
        
       }
     
        
        query.append(" ORDER BY createdtime offset " + ":offset " + "limit :limit"); 
        
        log.info("fetchPaymentIds query: " + query.toString() );
        return namedParameterJdbcTemplate.query(query.toString(), preparedStatementValues, new SingleColumnRowMapper<>(String.class));

    }

    public List<String> fetchPaymentIdsByCriteria(PaymentSearchCriteria paymentSearchCriteria) {
        Map<String, Object> preparedStatementValues = new HashMap<>();
        String query = paymentQueryBuilder.getIdQuery(paymentSearchCriteria, preparedStatementValues);
        return namedParameterJdbcTemplate.query(query, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
    }

	public List<String> fetchPropertyDetail(String consumerCode) {
		List<String> status = new ArrayList<String>();
		List<String> oldConnectionno = fetchOldConnectionNo(consumerCode);
		List<String> plotSize = fetchLandArea(consumerCode);
		List<String> usageCategory = fetchUsageCategory(consumerCode);
		if(oldConnectionno.size()>0)
		status.add(oldConnectionno.get(0));
		if(plotSize.size()>0)
		status.add(plotSize.get(0));
		if(usageCategory.size()>0)
		status.add(usageCategory.get(0));		
		return status;
	}

	public List<String> fetchOldConnectionNo(String consumerCode) {
		List<String> res = new ArrayList<>();
		String queryString = "select oldconnectionno from eg_ws_connection where connectionno='"+consumerCode+"'";
		log.info("Query: " +queryString);
		try {
		//	res = jdbcTemplate.queryForList(queryString, String.class);
			res = namedParameterJdbcTemplate.query(queryString, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading bill scheduler status" + ex.getMessage());
		}
		return res;
	}
	
	public List<String> fetchLandArea(String consumerCode) {
		List<String> res = new ArrayList<>();
		Map<String, Object> preparedStatementValues = new HashMap<>();
		String queryString = "select a2.landarea from eg_ws_connection a1 inner join eg_pt_property a2 on a1.property_id= a2.propertyid"
				+ " where a1.connectionno = '"+consumerCode+"'";
		log.info("Query: " +queryString);
		try {
			//res = jdbcTemplate.queryForList(queryString, String.class);
			res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading bill scheduler status" + ex.getMessage());
		}
		return res;
	}
	
	
	
	public List<String> fetchUsageCategory(String consumerCode) {
		List<String> res = new ArrayList<>();
		Map<String, Object> preparedStatementValues = new HashMap<>();
		String queryString = "select a2.usagecategory from eg_ws_connection a1 inner join eg_pt_property a2 on a1.property_id= a2.propertyid"
				+ " where a1.connectionno = '"+consumerCode+"'";
		log.info("Query: " +queryString);
		try {
		//	res = jdbcTemplate.queryForList(queryString, String.class);
			res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading bill scheduler status" + ex.getMessage());
		}
		return res;
	}






	
    
	public List<String> fetchUsageCategoryByApplicationno(Set<String> consumerCodes) {
		List<String> res = new ArrayList<>();
		String consumercode = null;
		 Iterator<String> iterate = consumerCodes.iterator();
		 while(iterate.hasNext()) {
			    consumercode =   iterate.next();			  
		}		
		Map<String, Object> preparedStatementValues = new HashMap<>();
		String queryString;
		if (consumercode.contains("WS_AP")) {
		    queryString = "select a2.usagecategory from eg_ws_connection a1 "
				+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
				+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
				+ " where a1.applicationno='"+consumercode+"'";
		log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
		} else {
			queryString = "select a2.usagecategory from eg_sw_connection a1 "
					+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
					+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
					+ " where a1.applicationno='"+consumercode+"'";
			log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
		}
		try {
			res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading usage category" + ex.getMessage());
		}
		return res;
	}
	public List<String> fetchAddressByApplicationno(Set<String> consumerCodes) {
		List<String> res = new ArrayList<>();
		String consumercode = null;
		 Iterator<String> iterate = consumerCodes.iterator();
		 while(iterate.hasNext()) {
			    consumercode =   iterate.next();			  
		}
		Map<String, Object> preparedStatementValues = new HashMap<>();
		String queryString;
		if (consumercode.contains("WS_AP")) {
		 queryString = "select CONCAT(doorno,buildingname,city) as address from eg_ws_connection a1 "
				+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
				+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
				+ " where a1.applicationno='"+consumercode+"'";
		log.info("Query for fetchAddressByApplicationno: " +queryString);
		}
		else {
			 queryString = "select CONCAT(doorno,buildingname,city) as address from eg_sw_connection a1 "
						+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
						+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
						+ " where a1.applicationno='"+consumercode+"'";
				log.info("Query for fetchAddressByApplicationno: " +queryString);
		}
		try {
			res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading usage category" + ex.getMessage());
		}
		return res;
	}

	
 //        //onetime fee
	
	// public List<String> fetchUsageCategoryByApplicationnos(Set<String> consumerCodes,String businesssrvice) {
	// 	List<String> res = new ArrayList<>();
	// 	String consumercode = null;
	// 	 Iterator<String> iterate = consumerCodes.iterator();
	// 	 while(iterate.hasNext()) {
	// 		    consumercode =   iterate.next();			  
	// 	}		
	// 	Map<String, Object> preparedStatementValues = new HashMap<>();
	// 	String queryString;
	// 	if (businesssrvice.contains("WS")) {
	// 		queryString = "select a2.usagecategory from eg_ws_connection a1 "
	// 				+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
	// 				+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
	// 				+ " where a1.connectionno in (select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill "
	// 				+ "	where bill.id=pd.billid "
	// 				+ "	 and pd.receiptnumber='"+consumercode+"')";
	// 	log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
	// 	} else {
	// 		queryString = "select a2.usagecategory from eg_sw_connection a1 "
	// 				+ " inner join eg_pt_property a2 on a1.property_id = a2.propertyid "
	// 				+ " inner join eg_pt_address a3 on a2.id=a3.propertyid "
	// 				+ " where a1.connectionno in (select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill "
	// 				+ "	where bill.id=pd.billid "
	// 				+ "	 and pd.receiptnumber='"+consumercode+"')";
	// 		log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
	// 	}
	// 	try {
	// 		res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
	// 	} catch (Exception ex) {
	// 		log.error("Exception while reading usage category" + ex.getMessage());
	// 	}
	// 	return res;
	// }
	// public List<String> fetchAddressByApplicationnos(Set<String> consumerCodes,String businesssrvice) {
	// 	List<String> res = new ArrayList<>();
	// 	String consumercode = null;
	// 	 Iterator<String> iterate = consumerCodes.iterator();
	// 	 while(iterate.hasNext()) {
	// 		    consumercode =   iterate.next();			  
	// 	}
	// 	Map<String, Object> preparedStatementValues = new HashMap<>();
	// 	String queryString;
	// 	if (businesssrvice.contains("WS")) {
	// 		 queryString = "SELECT TRIM(BOTH ',' FROM CONCAT_WS(',', "
	// 		 		+ "                NULLIF(doorno, ''), "
	// 		 		+ "                NULLIF(plotno, ''), "
	// 		 		+ "                NULLIF(buildingname, ''), "
	// 		 		+ "                NULLIF(street, ''), "
	// 		 		+ "                NULLIF(landmark, ''), "
	// 		 		+ "                NULLIF(city, ''), "
	// 		 		+ "                NULLIF(district, ''), "
	// 		 		+ "                NULLIF(region, ''), "
	// 		 		+ "                NULLIF(pincode, '') "
		
	// 		 		+ "            )) AS connectionno "
	// 		 		+ "FROM eg_ws_connection a1 "
	// 		 		+ "INNER JOIN eg_pt_property a2 ON a1.property_id = a2.propertyid "
	// 		 		+ "INNER JOIN eg_pt_address a3 ON a2.id = a3.propertyid  "
	// 		 		+ " where a1.connectionno in (select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill"
	// 					+ "	where bill.id=pd.billid "
	// 					+ " and pd.receiptnumber='"+consumercode+"')";
	// 	log.info("Query for fetchAddressByApplicationno: " +queryString);
	// 	}
	// 	else {
	// 		 queryString = "SELECT TRIM(BOTH ',' FROM CONCAT_WS(',', "
	// 			 		+ "                NULLIF(doorno, ''), "
	// 			 		+ "                NULLIF(plotno, ''), "
	// 			 		+ "                NULLIF(buildingname, ''), "
	// 			 		+ "                NULLIF(street, ''), "
	// 			 		+ "                NULLIF(landmark, ''), "
	// 			 		+ "                NULLIF(city, ''), "
	// 			 		+ "                NULLIF(district, ''), "
	// 			 		+ "                NULLIF(region, ''), "
	// 			 		+ "                NULLIF(pincode, '') "
			
	// 			 		+ "            )) AS connectionno "
	// 			 		+ "FROM eg_sw_connection a1 "
	// 			 		+ "INNER JOIN eg_pt_property a2 ON a1.property_id = a2.propertyid "
	// 			 		+ "INNER JOIN eg_pt_address a3 ON a2.id = a3.propertyid  "
	// 			 		+ " where a1.connectionno in (select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill"
	// 						+ "	where bill.id=pd.billid "
	// 						+ " and pd.receiptnumber='"+consumercode+"')";
	// 			log.info("Query for fetchAddressByApplicationno: " +queryString);
	// 	}
	// 	try {
	// 		res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
	// 	} catch (Exception ex) {
	// 		log.error("Exception while reading usage category" + ex.getMessage());
	// 	}
	// 	return res;
	// }
	
	// // for propertyid//
	
	// public List<String> fetchPropertyid(Set<String> consumerCodes,String businesssrvice) {
	// 	List<String> res = new ArrayList<>();
	// 	String consumercode = null;
	// 	 Iterator<String> iterate = consumerCodes.iterator();
	// 	 while(iterate.hasNext()) {
	// 		    consumercode =   iterate.next();			  
	// 	}		
	// 	Map<String, Object> preparedStatementValues = new HashMap<>();
	// 	String queryString;
	// 	if (businesssrvice.contains("WS")) {
	// 		queryString = "select property_id "
	// 				+ " FROM eg_ws_connection a1 "
	// 				+ " INNER JOIN eg_pt_property a2 ON a1.property_id = a2.propertyid "
	// 				+ " INNER JOIN eg_pt_address a3 ON a2.id = a3.propertyid "
	// 				+ " WHERE a1.connectionno in(select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill "
	// 				+ "	where bill.id=pd.billid "
	// 				+ "	 and pd.receiptnumber='"+consumercode+"')";
	// 	log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
	// 	} else {
	// 		queryString = "select property_id "
	// 				+ " FROM eg_sw_connection a1 "
	// 				+ " INNER JOIN eg_pt_property a2 ON a1.property_id = a2.propertyid "
	// 				+ " INNER JOIN eg_pt_address a3 ON a2.id = a3.propertyid "
	// 				+ " WHERE a1.connectionno in(select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill "
	// 				+ "	where bill.id=pd.billid "
	// 				+ "	 and pd.receiptnumber='"+consumercode+"')";
	// 		log.info("Query for fetchPaymentIdsByCriteria: " +queryString);
	// 	}
	// 	try {
	// 		res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
	// 	} catch (Exception ex) {
	// 		log.error("Exception while reading usage category" + ex.getMessage());
	// 	}
	// 	return res;
	// }

	public List<String> fetchConsumerCodeByReceiptNumber(String receiptnumber) {
		List<String> res = new ArrayList<>();
		Map<String, Object> preparedStatementValues = new HashMap<>();
		String queryString = "select bill.consumercode from egcl_paymentdetail pd, egcl_bill bill "
				+ " where bill.id=pd.billid  "
				+ " and pd.receiptnumber='"+receiptnumber+"'";
		log.info("Query: " +queryString);
		try {
			res = namedParameterJdbcTemplate.query(queryString, preparedStatementValues, new SingleColumnRowMapper<>(String.class));
		} catch (Exception ex) {
			log.error("Exception while reading usage category" + ex.getMessage());
		}
		return res;
	}
	
	
}

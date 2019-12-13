package org.egov.collection.repository.querybuilder;

import static java.util.stream.Collectors.toSet;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.egov.collection.model.Payment;
import org.egov.collection.model.PaymentDetail;
import org.egov.collection.model.PaymentSearchCriteria;
import org.egov.collection.web.contract.Bill;
import org.egov.collection.web.contract.BillAccountDetail;
import org.egov.collection.web.contract.BillDetail;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class PaymentQueryBuilder {



    public static final String SELECT_PAYMENT_SQL = "SELECT py.*,pyd.*,bill.*,bd.*,bacdt.*, " +
            "py.id as py_id,py.tenantId as py_tenantId,py.totalAmountPaid as py_totalAmountPaid,py.createdBy as py_createdBy,py.createdtime as py_createdtime," +
            "py.lastModifiedBy as py_lastModifiedBy,py.lastmodifiedtime as py_lastmodifiedtime,py.additionalDetails as py_additionalDetails," +
            "pyd.id as pyd_id, pyd.tenantId as pyd_tenantId, pyd.manualreceiptnumber as manualreceiptnumber,pyd.manualreceiptdate as manualreceiptdate, pyd.createdBy as pyd_createdBy,pyd.createdtime as pyd_createdtime,pyd.lastModifiedBy as pyd_lastModifiedBy," +
            "pyd.lastmodifiedtime as pyd_lastmodifiedtime,pyd.additionalDetails as pyd_additionalDetails," +
            "bill.createdby as bill_createdby,bill.createdtime as bill_createdtime,bill.lastmodifiedby as bill_lastmodifiedby," +
            "bill.lastmodifiedtime as bill_lastmodifiedtime,bill.id as bill_id," +
            "bill.status as bill_status,bill.additionalDetails as bill_additionalDetails," +
            "bill.tenantid as bill_tenantid,bill.totalamount as bill_totalamount," +
            "bd.id as bd_id,bd.tenantid as bd_tenantid,bd.additionalDetails as bd_additionalDetails," +
            "bacdt.id as bacdt_id,bacdt.tenantid as bacdt_tenantid, bacdt.amount as bacdt_amount, bacdt.adjustedamount as bacdt_adjustedamount, "
            + "bacdt.additionalDetails as bacdt_additionalDetails" +
            " FROM egcl_payment py  " +
            " INNER JOIN egcl_paymentdetail pyd ON pyd.paymentid = py.id " +
            " INNER JOIN egcl_bill bill ON bill.id = pyd.billid " +
            " INNER JOIN egcl_billdetial bd ON bd.billid = bill.id " +
            " INNER JOIN egcl_billaccountdetail bacdt ON bacdt.billdetailid = bd.id  ";

    private static final String PAGINATION_WRAPPER = "SELECT * FROM " +
            "(SELECT *, DENSE_RANK() OVER (ORDER BY py_id) offset_ FROM " +
            "({baseQuery})" +
            " result) result_offset " +
            "WHERE offset_ > :offset AND offset_ <= :limit";


    public static final String INSERT_PAYMENT_SQL = "INSERT INTO egcl_payment(" +
            "            id, tenantid, totaldue, totalamountpaid, transactionnumber, transactiondate, " +
            "            paymentmode, instrumentdate, instrumentnumber,instrumentStatus, ifsccode, additionaldetails, " +
            "            paidby, mobilenumber, payername, payeraddress, payeremail, payerid, " +
            "            paymentstatus, createdby, createdtime, lastmodifiedby, lastmodifiedtime)" +
            "            VALUES (:id, :tenantid, :totaldue, :totalamountpaid, :transactionnumber, :transactiondate, " +
            "            :paymentmode, :instrumentdate, :instrumentnumber, :instrumentStatus, :ifsccode, :additionaldetails," +
            "            :paidby, :mobilenumber, :payername, :payeraddress, :payeremail, :payerid, " +
            "            :paymentstatus, :createdby, :createdtime, :lastmodifiedby, :lastmodifiedtime);";


    public static final String INSERT_PAYMENTDETAIL_SQL = "INSERT INTO egcl_paymentdetail(" +
            "            id, tenantid, paymentid, due, amountpaid, receiptnumber, businessservice, " +
            "            billid, additionaldetails,receiptdate, receipttype, manualreceiptnumber, manualreceiptdate, createdby, createdtime, " +
            "            lastmodifiedby, lastmodifiedtime)" +
            "            VALUES (:id, :tenantid, :paymentid, :due, :amountpaid, :receiptnumber, :businessservice, " +
            "            :billid, :additionaldetails,:receiptdate, :receipttype, :manualreceiptnumber, :manualreceiptdate, :createdby, :createdtime," +
            "            :lastmodifiedby, :lastmodifiedtime);";


    public static final String INSERT_BILL_SQL = "INSERT INTO egcl_bill(" +
            "            id, status, iscancelled, additionaldetails, tenantid, collectionmodesnotallowed," +
            "            partpaymentallowed, isadvanceallowed, minimumamounttobepaid, " +
            "            businessservice, totalamount, consumercode, billnumber, billdate," +
            "            createdby, createdtime, lastmodifiedby, lastmodifiedtime) " +
            "    VALUES (:id, :status, :iscancelled, :additionaldetails, :tenantid, :collectionmodesnotallowed," +
            "            :partpaymentallowed, :isadvanceallowed, :minimumamounttobepaid," +
            "            :businessservice, :totalamount, :consumercode, :billnumber, :billdate," +
            "            :createdby, :createdtime, :lastmodifiedby, :lastmodifiedtime);";


    public static final String INSERT_BILLDETAIL_SQL = "INSERT INTO egcl_billdetial(" +
            "            id, tenantid, demandid, billid, amount, amountpaid, fromperiod," +
            "            toperiod, additionaldetails," +
            "            channel, voucherheader, boundary," +
            "            collectiontype," +
            "            billdescription, expirydate, displaymessage, callbackforapportioning," +
            "            cancellationremarks)" +
            "    VALUES (:id, :tenantid, :demandid, :billid, :amount, :amountpaid, :fromperiod," +
            "            :toperiod, :additionaldetails," +
            "            :channel, :voucherheader, :boundary," +
            "            :collectiontype," +
            "            :billdescription, :expirydate, :displaymessage, :callbackforapportioning," +
            "            :cancellationremarks);";


    public static final String INSERT_BILLACCOUNTDETAIL_SQL = "INSERT INTO egcl_billaccountdetail(" +
            "            id, tenantid, billdetailid, demanddetailid, " +
            "            \"order\", amount, adjustedamount, isactualdemand, taxheadcode, additionaldetails)" +
            "            VALUES (:id, :tenantid, :billdetailid, :demanddetailid, " +
            "            :order, :amount, :adjustedamount, :isactualdemand, :taxheadcode, :additionaldetails);";


    // Payment Status update queries

    public static final String STATUS_UPDATE_PAYMENT_SQL = "UPDATE egcl_payment SET instrumentstatus=:instrumentstatus,additionaldetails=:additionaldetails," +
            " paymentstatus=:paymentstatus, lastmodifiedby=:lastmodifiedby,lastmodifiedtime=:lastmodifiedtime" +
            " WHERE id=:id;";

    public static final String STATUS_UPDATE_PAYMENTDETAIL_SQL = "UPDATE egcl_paymentdetail SET  additionaldetails=:additionaldetails, lastmodifiedby=:lastmodifiedby, lastmodifiedtime=:lastmodifiedtime " +
            " WHERE id=:id;";

    public static final String STATUS_UPDATE_BILL_SQL = "UPDATE egcl_bill " +
            "   SET  status= :status, iscancelled= :iscancelled, additionaldetails= :additionaldetails, lastmodifiedby= :lastmodifiedby, lastmodifiedtime=:lastmodifiedtime" +
            "   WHERE id=:id;";

    public static final String COPY_PAYMENT_SQL = "INSERT INTO egcl_payment_audit SELECT * FROM egcl_payment WHERE id = :id;";

    public static final String COPY_PAYMENTDETAIL_SQL = "INSERT INTO egcl_paymentdetail_audit SELECT id, tenantid, paymentid, due, amountpaid, receiptnumber, "
    		+ "businessservice, billid, additionaldetails,  createdby, createdtime, lastmodifiedby, lastmodifiedtime, manualreceiptnumber, "
    		+ "manualreceiptdate, receiptdate, receipttype FROM egcl_paymentdetail WHERE id = :id ;";

    public static final String COPY_BILL_SQL = "INSERT INTO egcl_bill_audit SELECT * FROM egcl_bill WHERE id = :id;";

    public static final String COPY_BILLDETAIL_SQL = "INSERT INTO egcl_billdetial_audit SELECT * FROM egcl_billdetial WHERE id = :id;";



    // Payment update queries

    public static final String UPDATE_PAYMENT_SQL = "UPDATE egcl_payment SET additionaldetails=:additionaldetails, paidby=:paidby, payername=:payername," +
            " payeraddress=:payeraddress, payeremail=:payeremail, payerid=:payerid,paymentstatus=:paymentstatus, createdby=:createdby, createdtime=:createdtime," +
            " lastmodifiedby=:lastmodifiedby, lastmodifiedtime=:lastmodifiedtime WHERE id=:id ";

    public static final String UPDATE_PAYMENTDETAIL_SQL ="UPDATE egcl_paymentdetail SET additionaldetails=:additionaldetails, createdby=:createdby," +
            "createdtime=:createdtime, lastmodifiedby=:lastmodifiedby, lastmodifiedtime=:lastmodifiedtime" +
            "WHERE id=:id;";

    public static final String UPDATE_BILL_SQL = "UPDATE egcl_bill SET additionaldetails=:additionaldetails,createdby=:createdby,createdtime=:createdtime, lastmodifiedby=:lastmodifiedby,\n" +
            "lastmodifiedtime=:lastmodifiedtime WHERE id=:id;";

    public static final String UPDATE_BILLDETAIL_SQL = "UPDATE egcl_billdetial SET additionaldetails=:additionaldetails, voucherheader=:voucherheader," +
            " manualreceiptnumber=:manualreceiptnumber, manualreceiptdate=:manualreceiptdate, billdescription=:billdescription,displaymessage=:displaymessage," +
            "createdby=:createdby, createdtime=:createdtime, lastmodifiedby=:lastmodifiedby,lastmodifiedtime=:lastmodifiedtime WHERE id=:id ";
    
    
	public static final String BILL_BASE_QUERY = "SELECT b.id AS b_id, b.tenantid AS b_tenantid, b.iscancelled AS b_iscancelled, b.businessservice AS b_businessservice, "
			+ "b.billnumber AS b_billnumber, b.billdate AS b_billdate, b.consumercode AS b_consumercode, b.createdby AS b_createdby, b.status as b_status, b.minimumamounttobepaid AS b_minimumamounttobepaid, "
			+ "b.totalamount AS b_totalamount, b.partpaymentallowed AS b_partpaymentallowed, b.isadvanceallowed as b_isadvanceallowed, "
			+ "b.collectionmodesnotallowed AS b_collectionmodesnotallowed, b.createdtime AS b_createdtime, b.lastmodifiedby AS b_lastmodifiedby, "
			+ "b.lastmodifiedtime AS b_lastmodifiedtime, bd.id AS bd_id, bd.billid AS bd_billid, bd.tenantid AS bd_tenantid, bd.demandid, "
			+ "bd.fromperiod, bd.toperiod, bd.billdescription AS bd_billdescription, bd.displaymessage AS bd_displaymessage, bd.amount AS bd_amount, bd.amountpaid AS bd_amountpaid, "
			+ "bd.callbackforapportioning AS bd_callbackforapportioning, bd.expirydate AS bd_expirydate, ad.id AS ad_id, ad.tenantid AS ad_tenantid, "
			+ "ad.billdetailid AS ad_billdetailid, ad.order AS ad_order, ad.amount AS ad_amount, ad.adjustedamount AS ad_adjustedamount, "
			+ "ad.taxheadcode AS ad_taxheadcode, ad.demanddetailid as ad_demanddetailid, ad.isactualdemand AS ad_isactualdemand, b.additionaldetails as b_additionaldetails,  "
			+ "bd.additionaldetails as bd_additionaldetails,  ad.additionaldetails as ad_additionaldetails "
			+ "FROM egcl_bill b LEFT OUTER JOIN egcl_billdetial bd ON b.id = bd.billid AND b.tenantid = bd.tenantid "
			+ "LEFT OUTER JOIN egcl_billaccountdetail ad ON bd.id = ad.billdetailid AND bd.tenantid = ad.tenantid "
			+ "WHERE b.id IN (:id);"; 



	public static String getBillQuery() {
		return BILL_BASE_QUERY;
	}
	
	
    public static MapSqlParameterSource getParametersForPaymentCreate(Payment payment) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", payment.getId());
        sqlParameterSource.addValue("tenantid", payment.getTenantId());
        sqlParameterSource.addValue("totaldue", payment.getTotalDue());
        sqlParameterSource.addValue("totalamountpaid", payment.getTotalAmountPaid());
        sqlParameterSource.addValue("transactionnumber", payment.getTransactionNumber());
        sqlParameterSource.addValue("transactiondate", payment.getTransactionDate());
        sqlParameterSource.addValue("paymentmode", payment.getPaymentMode().toString());
        sqlParameterSource.addValue("instrumentdate", payment.getInstrumentDate());
        sqlParameterSource.addValue("instrumentnumber", payment.getInstrumentNumber());
        sqlParameterSource.addValue("instrumentStatus", payment.getInstrumentStatus().toString());
        sqlParameterSource.addValue("ifsccode", payment.getIfscCode());
        sqlParameterSource.addValue("additionaldetails", getJsonb(payment.getAdditionalDetails()));
        sqlParameterSource.addValue("paidby", payment.getPaidBy());
        sqlParameterSource.addValue("mobilenumber", payment.getMobileNumber());
        sqlParameterSource.addValue("payername", payment.getPayerName());
        sqlParameterSource.addValue("payeraddress", payment.getPayerAddress());
        sqlParameterSource.addValue("payeremail", payment.getPayerEmail());
        sqlParameterSource.addValue("payerid", payment.getPayerId());
        sqlParameterSource.addValue("paymentstatus", payment.getPaymentStatus().toString());
        sqlParameterSource.addValue("createdby", payment.getAuditDetails().getCreatedBy());
        sqlParameterSource.addValue("createdtime", payment.getAuditDetails().getCreatedTime());
        sqlParameterSource.addValue("lastmodifiedby", payment.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", payment.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;

    }


    public static MapSqlParameterSource getParametersForPaymentDetailCreate(String paymentId,PaymentDetail paymentDetail) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", paymentDetail.getId());
        sqlParameterSource.addValue("tenantid", paymentDetail.getTenantId());
        sqlParameterSource.addValue("paymentid", paymentId);
        sqlParameterSource.addValue("due", paymentDetail.getTotalDue());
        sqlParameterSource.addValue("amountpaid", paymentDetail.getTotalAmountPaid());
        sqlParameterSource.addValue("receiptnumber", paymentDetail.getReceiptNumber());
        sqlParameterSource.addValue("businessservice", paymentDetail.getBusinessService());
        sqlParameterSource.addValue("billid", paymentDetail.getBillId());
        sqlParameterSource.addValue("additionaldetails", getJsonb(paymentDetail.getAdditionalDetails()));
        sqlParameterSource.addValue("receiptdate", paymentDetail.getReceiptDate());
        sqlParameterSource.addValue("receipttype", paymentDetail.getReceiptType());
        sqlParameterSource.addValue("manualreceiptnumber", paymentDetail.getManualReceiptNumber());
        sqlParameterSource.addValue("manualreceiptdate", paymentDetail.getManualReceiptDate());
        sqlParameterSource.addValue("createdby", paymentDetail.getAuditDetails().getCreatedBy());
        sqlParameterSource.addValue("createdtime", paymentDetail.getAuditDetails().getCreatedTime());
        sqlParameterSource.addValue("lastmodifiedby", paymentDetail.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", paymentDetail.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;

    }



    public static MapSqlParameterSource getParamtersForBillCreate(Bill bill){

        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", bill.getId());
        sqlParameterSource.addValue("status", bill.getStatus().toString());
        sqlParameterSource.addValue("iscancelled", bill.getIsCancelled());
        sqlParameterSource.addValue("additionaldetails",getJsonb(bill.getAdditionalDetails()));
        sqlParameterSource.addValue("tenantid", bill.getTenantId());
        sqlParameterSource.addValue("collectionmodesnotallowed", StringUtils.join(bill.getCollectionModesNotAllowed(),","));
        sqlParameterSource.addValue("partpaymentallowed", bill.getPartPaymentAllowed());
        sqlParameterSource.addValue("isadvanceallowed", bill.getIsAdvanceAllowed());
        sqlParameterSource.addValue("minimumamounttobepaid", bill.getMinimumAmountToBePaid());
        sqlParameterSource.addValue("businessservice", bill.getBusinessService());
        sqlParameterSource.addValue("totalamount", bill.getTotalAmount());
        sqlParameterSource.addValue("consumercode", bill.getConsumerCode());
        sqlParameterSource.addValue("billnumber", bill.getBillNumber());
        sqlParameterSource.addValue("billdate", bill.getBillDate());
        sqlParameterSource.addValue("reasonforcancellation", bill.getReasonForCancellation());
        sqlParameterSource.addValue("createdby", bill.getAuditDetails().getCreatedBy());
        sqlParameterSource.addValue("createdtime", bill.getAuditDetails().getCreatedTime());
        sqlParameterSource.addValue("lastmodifiedby", bill.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", bill.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;
    }



    public static MapSqlParameterSource getParamtersForBillDetailCreate(BillDetail billDetail) {

        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();
        sqlParameterSource.addValue("id", billDetail.getId());
        sqlParameterSource.addValue("tenantid", billDetail.getTenantId());
        sqlParameterSource.addValue("demandid", billDetail.getDemandId());
        sqlParameterSource.addValue("billid", billDetail.getBillId());
        sqlParameterSource.addValue("amount", billDetail.getAmount());
        sqlParameterSource.addValue("amountpaid", billDetail.getAmountPaid());
        sqlParameterSource.addValue("fromperiod", billDetail.getFromPeriod());
        sqlParameterSource.addValue("toperiod", billDetail.getToPeriod());
        sqlParameterSource.addValue("additionaldetails", getJsonb(billDetail.getAdditionalDetails()));
        sqlParameterSource.addValue("channel", billDetail.getChannel());
        sqlParameterSource.addValue("voucherheader", billDetail.getVoucherHeader());
        sqlParameterSource.addValue("boundary", billDetail.getBoundary());
        sqlParameterSource.addValue("manualreceiptnumber", billDetail.getManualReceiptNumber());
        sqlParameterSource.addValue("manualreceiptdate", billDetail.getManualReceiptDate());
        sqlParameterSource.addValue("collectiontype", billDetail.getCollectionType());
        sqlParameterSource.addValue("billdescription", billDetail.getBillDescription());
        sqlParameterSource.addValue("expirydate", billDetail.getExpiryDate());
        sqlParameterSource.addValue("displaymessage", billDetail.getDisplayMessage());
        sqlParameterSource.addValue("callbackforapportioning", billDetail.getCallBackForApportioning());
        sqlParameterSource.addValue("cancellationremarks", billDetail.getCancellationRemarks());

        return sqlParameterSource;
    }


    public static MapSqlParameterSource getParametersForBillAccountDetailCreate(BillAccountDetail billAccountDetail) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", billAccountDetail.getId());
        sqlParameterSource.addValue("tenantid", billAccountDetail.getTenantId());
        sqlParameterSource.addValue("billdetailid", billAccountDetail.getBillDetailId());
        sqlParameterSource.addValue("demanddetailid", billAccountDetail.getDemandDetailId());
        sqlParameterSource.addValue("order", billAccountDetail.getOrder());
        sqlParameterSource.addValue("amount", billAccountDetail.getAmount());
        sqlParameterSource.addValue("adjustedamount", billAccountDetail.getAdjustedAmount());
        sqlParameterSource.addValue("isactualdemand", billAccountDetail.getIsActualDemand());
        sqlParameterSource.addValue("taxheadcode", billAccountDetail.getTaxHeadCode());
        sqlParameterSource.addValue("additionaldetails", getJsonb(billAccountDetail.getAdditionalDetails()));

        return sqlParameterSource;
    }



    public static String getPaymentSearchQuery(PaymentSearchCriteria searchCriteria,
                                               Map<String, Object> preparedStatementValues) {
        StringBuilder selectQuery = new StringBuilder(SELECT_PAYMENT_SQL);

        addWhereClause(selectQuery, preparedStatementValues, searchCriteria);

        addOrderByClause(selectQuery);

        return addPaginationClause(selectQuery, preparedStatementValues, searchCriteria);
    }


    private static void addWhereClause(StringBuilder selectQuery, Map<String, Object> preparedStatementValues,
                                       PaymentSearchCriteria searchCriteria) {

        if (StringUtils.isNotBlank(searchCriteria.getTenantId())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            if(searchCriteria.getTenantId().split("\\.").length > 1) {
                selectQuery.append(" py.tenantId =:tenantId");
                preparedStatementValues.put("tenantId", searchCriteria.getTenantId());
            }
            else {
                selectQuery.append(" py.tenantId LIKE :tenantId");
                preparedStatementValues.put("tenantId", searchCriteria.getTenantId() + "%");
            }

        }
        
        if(!CollectionUtils.isEmpty(searchCriteria.getIds())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.id IN (:id)  ");
            preparedStatementValues.put("id", searchCriteria.getIds());	
        }

        if (searchCriteria.getReceiptNumbers() != null && !searchCriteria.getReceiptNumbers().isEmpty()) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" pyd.receiptNumber IN (:receiptnumber)  ");
            preparedStatementValues.put("receiptnumber", searchCriteria.getReceiptNumbers());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getStatus())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" UPPER(py.paymentstatus) in (:status)");
            preparedStatementValues.put("status",
                    searchCriteria.getStatus()
                            .stream()
                            .map(String::toUpperCase)
                            .collect(toSet())
            );
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getInstrumentStatus())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" UPPER(py.instrumentStatus) in (:instrumentStatus)");
            preparedStatementValues.put("instrumentStatus",
                    searchCriteria.getInstrumentStatus()
                            .stream()
                            .map(String::toUpperCase)
                            .collect(toSet())
            );
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getPaymentModes())) {

            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" UPPER(py.paymentMode) in (:paymentMode)");
            preparedStatementValues.put("paymentMode",
                    searchCriteria.getPaymentModes()
                            .stream()
                            .map(String::toUpperCase)
                            .collect(toSet())
            );
        }

        if (StringUtils.isNotBlank(searchCriteria.getMobileNumber())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.mobileNumber = :mobileNumber");
            preparedStatementValues.put("mobileNumber", searchCriteria.getMobileNumber());
        }

        if (StringUtils.isNotBlank(searchCriteria.getTransactionNumber())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.transactionNumber = :transactionNumber");
            preparedStatementValues.put("transactionNumber", searchCriteria.getTransactionNumber());
        }

        if (searchCriteria.getFromDate() != null) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.transactionDate >= :fromDate");
            preparedStatementValues.put("fromDate", searchCriteria.getFromDate());
        }

        if (searchCriteria.getToDate() != null) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.transactionDate <= :toDate");
            Calendar c = Calendar.getInstance();
            c.setTime(new Date(searchCriteria.getToDate()));
            c.add(Calendar.DATE, 1);
            searchCriteria.setToDate(c.getTime().getTime());

            preparedStatementValues.put("toDate", searchCriteria.getToDate());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getPayerIds())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" py.payerid IN (:payerid)  ");
            preparedStatementValues.put("payerid", searchCriteria.getPayerIds());
        }
        
        if (!CollectionUtils.isEmpty(searchCriteria.getBusinessServices())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" pyd.businessService IN (:businessService)  ");
            preparedStatementValues.put("businessService", searchCriteria.getBusinessServices());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getConsumerCodes())) {

            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" bill.consumerCode in (:consumerCodes)");
            preparedStatementValues.put("consumerCodes", searchCriteria.getConsumerCodes());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getBillIds())) {
            addClauseIfRequired(preparedStatementValues, selectQuery);
            selectQuery.append(" pyd.billid in (:billid)");
            preparedStatementValues.put("billid", searchCriteria.getBillIds());
        }

    }


    private static String addPaginationClause(StringBuilder selectQuery, Map<String, Object> preparedStatementValues,
                                              PaymentSearchCriteria criteria) {

            String finalQuery = PAGINATION_WRAPPER.replace("{baseQuery}", selectQuery);
            preparedStatementValues.put("offset", criteria.getOffset());
            preparedStatementValues.put("limit", criteria.getOffset() + criteria.getLimit());

            return finalQuery;
    }

    private static String addOrderByClause(StringBuilder selectQuery) {
        return selectQuery.append(" ORDER BY py.transactiondate DESC ").toString();
    }


    private static void addClauseIfRequired(Map<String, Object> values, StringBuilder queryString) {
        if (values.isEmpty())
            queryString.append(" WHERE ");
        else {
            queryString.append(" AND");
        }
    }



    public static MapSqlParameterSource getParamtersForBillStatusUpdate(Bill bill){

        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", bill.getId());
        sqlParameterSource.addValue("status", bill.getStatus());
        sqlParameterSource.addValue("iscancelled", bill.getIsCancelled());
        sqlParameterSource.addValue("additionaldetails", getJsonb(bill.getAdditionalDetails()));
        sqlParameterSource.addValue("status", bill.getStatus().toString());
        sqlParameterSource.addValue("reasonforcancellation", bill.getReasonForCancellation());
        sqlParameterSource.addValue("lastmodifiedby", bill.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", bill.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;
    }


    public static MapSqlParameterSource getParametersForPaymentDetailStatusUpdate(PaymentDetail paymentDetail) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", paymentDetail.getId());
        sqlParameterSource.addValue("additionaldetails",getJsonb(paymentDetail.getAdditionalDetails()));
        sqlParameterSource.addValue("lastmodifiedby", paymentDetail.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", paymentDetail.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;

    }


    public static MapSqlParameterSource getParametersForPaymentStatusUpdate(Payment payment) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", payment.getId());
        sqlParameterSource.addValue("instrumentstatus", payment.getInstrumentStatus().toString());
        sqlParameterSource.addValue("paymentstatus", payment.getPaymentStatus().toString());
        sqlParameterSource.addValue("additionaldetails", getJsonb(payment.getAdditionalDetails()));
        sqlParameterSource.addValue("lastmodifiedby", payment.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", payment.getAuditDetails().getLastModifiedTime());
        
        return sqlParameterSource;

    }



    // Payment update

    public static MapSqlParameterSource getParametersForPaymentUpdate(Payment payment) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", payment.getId());
        sqlParameterSource.addValue("paidby", payment.getPaidBy());
        sqlParameterSource.addValue("payeraddress", payment.getPayerAddress());
        sqlParameterSource.addValue("payeremail", payment.getPayerEmail());
        sqlParameterSource.addValue("payername", payment.getPayerName());
        sqlParameterSource.addValue("additionalDetails", getJsonb(payment.getAdditionalDetails()));
        sqlParameterSource.addValue("lastmodifiedby", payment.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", payment.getAuditDetails().getLastModifiedTime());


        return sqlParameterSource;

    }


    public static MapSqlParameterSource getParametersForPaymentDetailUpdate(PaymentDetail paymentDetail) {
        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", paymentDetail.getId());
        sqlParameterSource.addValue("additionalDetails", getJsonb(paymentDetail.getAdditionalDetails()));
        sqlParameterSource.addValue("lastmodifiedby", paymentDetail.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", paymentDetail.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;

    }


    public static MapSqlParameterSource getParamtersForBillUpdate(Bill bill){

        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();

        sqlParameterSource.addValue("id", bill.getId());
        sqlParameterSource.addValue("additionaldetails", getJsonb(bill.getAdditionalDetails()) );
        sqlParameterSource.addValue("createdby", bill.getAuditDetails().getCreatedBy());
        sqlParameterSource.addValue("createdtime", bill.getAuditDetails().getCreatedTime());
        sqlParameterSource.addValue("lastmodifiedby", bill.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", bill.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;
    }


    public static MapSqlParameterSource getParamtersForBillDetailUpdate(BillDetail billDetail) {

        MapSqlParameterSource sqlParameterSource = new MapSqlParameterSource();
        sqlParameterSource.addValue("id", billDetail.getId());
        sqlParameterSource.addValue("additionaldetails", getJsonb(billDetail.getAdditionalDetails()));
        sqlParameterSource.addValue("voucherheader", billDetail.getVoucherHeader());
        sqlParameterSource.addValue("manualreceiptnumber", billDetail.getManualReceiptNumber());
        sqlParameterSource.addValue("manualreceiptdate", billDetail.getManualReceiptDate());
        sqlParameterSource.addValue("billdescription", billDetail.getBillDescription());
        sqlParameterSource.addValue("displaymessage", billDetail.getDisplayMessage());
        sqlParameterSource.addValue("createdby", billDetail.getAuditDetails().getCreatedBy());
        sqlParameterSource.addValue("createdtime", billDetail.getAuditDetails().getCreatedTime());
        sqlParameterSource.addValue("lastmodifiedby", billDetail.getAuditDetails().getLastModifiedBy());
        sqlParameterSource.addValue("lastmodifiedtime", billDetail.getAuditDetails().getLastModifiedTime());

        return sqlParameterSource;
    }





    private static PGobject getJsonb(JsonNode node) {
        if (Objects.isNull(node))
            return null;

        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(node.toString());
            return pgObject;
        } catch (SQLException e) {
            throw new CustomException("UNABLE_TO_CREATE_RECEIPT", "Invalid JSONB value provided");
        }

    }












}

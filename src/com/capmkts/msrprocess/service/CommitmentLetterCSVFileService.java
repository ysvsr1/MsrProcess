package com.capmkts.msrprocess.service;

import java.io.File;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.capmkts.msrprocess.constants.MsrConstants;
import com.capmkts.msrprocess.dao.AgencyCommitmentLetterDAO;
import com.capmkts.msrprocess.dao.CMCFileDAO;
import com.capmkts.msrprocess.dao.CommitmentLetterDAO;
import com.capmkts.msrprocess.dao.FHLMCCommitmentLetterDAO;
import com.capmkts.msrprocess.dao.PatronCompanyDAO;
import com.capmkts.msrprocess.data.AgencyCommitmentLetter;
import com.capmkts.msrprocess.data.FHLMCCommitmentLetter;
import com.capmkts.msrprocess.util.DataConversionUtil;
import com.capmkts.msrprocess.util.DateUtil;
import com.capmkts.msrprocess.validator.CommitmentLetterValidator;
import com.capmkts.msrprocess.validator.DataValidator;

public class CommitmentLetterCSVFileService implements FileService {

	@Override
	public void process(File file, DataValidator dataValidator) throws Exception{
		DataConversionUtil dataConversionUtil = new DataConversionUtil();
		String fileContent = "";
		boolean fileValid = false;
		// Read file to string
		if (file.getName().endsWith("xls") || file.getName().endsWith("xlsx")){
			fileContent = dataConversionUtil.getExcelToCSVString(file);
			fileValid = true;
		}
		else{
			fileContent = FileUtils.readFileToString(file);
			fileValid = true;
		}
		
		if (fileValid){
			//Check for FreddieMac Commitment Letter
			String[] recordArray = fileContent.split("\\n");
			String[] headerArray = recordArray[0].split("\\,");
			
			//Freddie Mac
			if (headerArray[0].contains("Allocable")){
				FHLMCCommitmentLetter fhlmcCommitmentLetter = prepareFHLMCAgencyCommitmentLetter(recordArray);
				
				//Commit to database
				FHLMCCommitmentLetterDAO fhlmcCommitmentLetterDAO = new FHLMCCommitmentLetterDAO();
				fhlmcCommitmentLetterDAO.save(fhlmcCommitmentLetter);
	
				//Extract and move data to AgencyCommitmentLetter_Staging
				boolean success = fhlmcCommitmentLetterDAO.moveToAgencyCommitLetterTbl();
				
				//Get Lender Name
				PatronCompanyDAO patronCompanyDAO = new PatronCompanyDAO();
				Integer sellerLenderNumber = fhlmcCommitmentLetter.getDesignatedServicerNumber();
				String lenderName = patronCompanyDAO.getPatronCompanyByLenderNum(sellerLenderNumber);
				System.out.println("\n***** Seller Lender Number: " +sellerLenderNumber);
				System.out.println("\n***** Seller Lender Name: " +lenderName);
				AgencyCommitmentLetterDAO agencyCommitmentLetterDAO = new AgencyCommitmentLetterDAO();
				
				agencyCommitmentLetterDAO.updateLenderName(lenderName, sellerLenderNumber);
				
				// Save file
				if (success){
					CMCFileDAO cmcFileDAO = new CMCFileDAO();
					cmcFileDAO.saveFile(file, MsrConstants.FHLMC_COMMITMENT_LETTER, true, "FHLMC content", null,
							fhlmcCommitmentLetter.getMasterCommitmentNumber().replaceAll("[^\\d.]", ""));
					dataValidator.addMessage("Thank you for submitting your commitment");
				}
				else{
					dataValidator.addMessage("Invalid data. Please check your file.");
				}
			}
			
			//Set Loans
			else if (headerArray[0].contains("Due Date")){
				dataValidator.addMessage("Please select 'CMC Servicing Date File/Agency PA' then reupload your SET file.");
			}
			
			//Fannie Mae
			else {
					
				AgencyCommitmentLetter agencyCommitmentLetter = readCSVFile(file);
		
				CommitmentLetterValidator commitmentLetterValidator = new CommitmentLetterValidator();
				
				// Patron per month cap amount validation 
				if(!commitmentLetterValidator.isPatronCapAmountReached(agencyCommitmentLetter,dataValidator)){
					
					CommitmentLetterDAO commitmentLetterDAO = new CommitmentLetterDAO();
					commitmentLetterDAO.save(agencyCommitmentLetter);
			
					// Save file
					CMCFileDAO cmcFileDAO = new CMCFileDAO();
					cmcFileDAO.saveFile(file, MsrConstants.COMMITMENT_LETTER, dataValidator
							.isValid(), dataValidator.getMessageList().toString(), null,
							agencyCommitmentLetter.getAgencyCommitmentID());
					
					dataValidator.addMessage("Thank you for submitting your commitment");
				}
				else{
					dataValidator.addMessage("Your commitment request has been denied. You have exceeded your 30% portfolio cap.");
				}
			}
		}
	}

	private FHLMCCommitmentLetter prepareFHLMCAgencyCommitmentLetter(String[] recordArray) {
		FHLMCCommitmentLetter fhlmcCommitmentLetter = new FHLMCCommitmentLetter();
		String[] contentArray = recordArray[1].split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
		
		for (int i=0; i<contentArray.length; i++){
		switch(i){
			case 0:
				fhlmcCommitmentLetter.setAllocableLoanAdjustablePeriodServicingFeeMax(contentArray[i]);
				break;
			case 1:
				fhlmcCommitmentLetter.setAllocableLoanAdjustablePeriodServicingFeeMin(contentArray[i]);
				break;
			case 2:
				fhlmcCommitmentLetter.setAllocableLoanFixedPeriodServicingFeeMax(parseBigDecimal(contentArray[i]));
				break;
			case 3:
				fhlmcCommitmentLetter.setAllocableLoanFixedPeriodServicingFeeMin(parseBigDecimal(contentArray[i]));
				break;
			case 4:
				fhlmcCommitmentLetter.setBubdRequestType(contentArray[i]);
				break;
			case 5:
				fhlmcCommitmentLetter.setBuydownAmountBPS(contentArray[i]);
				break;
			case 6:
				fhlmcCommitmentLetter.setBuyupMax(contentArray[i]);
				break;
			case 7:
				fhlmcCommitmentLetter.setContractAcceptedDate(parseDate(contentArray[i]));
				break;
			case 8:
				fhlmcCommitmentLetter.setContractAmount(parseBigDecimal(contentArray[i]));
				break;
			case 9:
				fhlmcCommitmentLetter.setContractAssumabilityIndicator(parseBoolean(contentArray[i]));
				break;
			case 10:
				fhlmcCommitmentLetter.setContractAssumabilityType(contentArray[i]);
				break;
			case 11:
				fhlmcCommitmentLetter.setContractExpirationDate(parseDate(contentArray[i]));
				break;
			case 12:
				fhlmcCommitmentLetter.setContractIndexLookbackDays(contentArray[i]);
				break;
			case 13:
				fhlmcCommitmentLetter.setContractName(contentArray[i]);
				break;
			case 14:
				fhlmcCommitmentLetter.setContractPPMIndicator(parseBoolean(contentArray[i]));
				break;
			case 15:
				fhlmcCommitmentLetter.setContractPrice1(parseBigDecimal(contentArray[i]));
				break;
			case 16:
				fhlmcCommitmentLetter.setContractPrice2(parseBigDecimal(contentArray[i]));
				break;
			case 17:
				fhlmcCommitmentLetter.setContractPrice3(parseBigDecimal(contentArray[i]));
				break;
			case 18:
				fhlmcCommitmentLetter.setContractPrice4(parseBigDecimal(contentArray[i]));
				break;
			case 19:
				fhlmcCommitmentLetter.setContractPrice5(parseBigDecimal(contentArray[i]));
				break;
			case 20:
				fhlmcCommitmentLetter.setContractRemainingBalance(parseBigDecimal(contentArray[i]));
				break;
			case 21:
				fhlmcCommitmentLetter.setContractRemainingBalanceToFulfill(parseBigDecimal(contentArray[i]));
				break;
			case 22:
				fhlmcCommitmentLetter.setContractServicingOption(contentArray[i]);
				break;
			case 23:
				fhlmcCommitmentLetter.setContractStatus(contentArray[i]);
				break;
			case 24:
				fhlmcCommitmentLetter.setContractWeightedAverageAdjustablePeriodServicingFee(contentArray[i]);
				break;
			case 25:
				fhlmcCommitmentLetter.setContractWeightedAverageMonthsToNextRateAdjustmentDate(contentArray[i]);
				break;
			case 26:
				fhlmcCommitmentLetter.setCusipNumber(contentArray[i]);
				break;
			case 27:
				fhlmcCommitmentLetter.setDesignatedServicerNumber(parseInteger(contentArray[i]));
				break;
			case 28:
				fhlmcCommitmentLetter.setExecutionMethodType(contentArray[i]);
				break;
			case 29:
				fhlmcCommitmentLetter.setExportCreatedDateTime(parseDate(contentArray[i]));
				break;
			case 30:
				fhlmcCommitmentLetter.setFinalDeliveryDate(parseDate(contentArray[i]));
				break;
			case 31:
				fhlmcCommitmentLetter.setFMApprovalStatus(contentArray[i]);
				break;
			case 32:
				fhlmcCommitmentLetter.setGoldRushFeeAmount(parseBigDecimal(contentArray[i]));
				break;
			case 33:
				fhlmcCommitmentLetter.setGoldRushFeeRate(parseBigDecimal(contentArray[i]));
				break;
			case 34:
				fhlmcCommitmentLetter.setGuarantorSettlementDate(parseDate(contentArray[i]));
				break;
			case 35:
				fhlmcCommitmentLetter.setInterestRateRange1(parseBigDecimal(contentArray[i]));
				break;
			case 36:
				fhlmcCommitmentLetter.setInterestRateRange2(parseBigDecimal(contentArray[i]));
				break;
			case 37:
				fhlmcCommitmentLetter.setInterestRateRange3(parseBigDecimal(contentArray[i]));
				break;
			case 38:
				fhlmcCommitmentLetter.setInterestRateRange4(parseBigDecimal(contentArray[i]));
				break;
			case 39:
				fhlmcCommitmentLetter.setInterestRateRange5(parseBigDecimal(contentArray[i]));
				break;
			case 40:
				fhlmcCommitmentLetter.setInterestRateType(contentArray[i]);
				break;
			case 41:
				fhlmcCommitmentLetter.setInterestPrincipalRemittanceType(contentArray[i]);
				break;
			case 42:
				fhlmcCommitmentLetter.setInvestorContractIdentifier(contentArray[i]);
				break;
			case 43:
				fhlmcCommitmentLetter.setLLPABuyupMaxAMinus(contentArray[i]);
				break;
			case 44:
				fhlmcCommitmentLetter.setLoanCount(contentArray[i]);
				break;
			case 45:
				fhlmcCommitmentLetter.setMasterCommitmentNumber(contentArray[i]);
				break;
			case 46:
				fhlmcCommitmentLetter.setMaturityTerm(parseBigDecimal(contentArray[i]));
				break;
			case 47:
				fhlmcCommitmentLetter.setMaximumContractInterestRate(parseDouble(contentArray[i]));
				break;
			case 48:
				fhlmcCommitmentLetter.setMinimumContractInterestRate(parseDouble(contentArray[i]));
				break;
			case 49:
				fhlmcCommitmentLetter.setMinimumRequiredServicingSpreadCoupon(parseDouble(contentArray[i]));
				break;
			case 50:
				fhlmcCommitmentLetter.setMinimumRequiredServicingSpreadLifetimeCeiling(contentArray[i]);
				break;
			case 51:
				fhlmcCommitmentLetter.setMinimumRequiredServicingSpreadMargin(contentArray[i]);
				break;
			case 52:
				fhlmcCommitmentLetter.setPairoffAmount(parseBigDecimal(contentArray[i]));
				break;
			case 53:
				fhlmcCommitmentLetter.setPoolNumber(contentArray[i]);
				break;
			case 54:
				fhlmcCommitmentLetter.setPrepaymentRemittanceDueDays(contentArray[i]);
				break;
			case 55:
				fhlmcCommitmentLetter.setPricingProduct(contentArray[i]);
				break;
			case 56:
				fhlmcCommitmentLetter.setRemittanceDueDay(contentArray[i]);
				break;
			case 57:
				fhlmcCommitmentLetter.setRemittanceOption(contentArray[i]);
				break;
			case 58:
				fhlmcCommitmentLetter.setRequiredSpreadGfee(contentArray[i]);
				break;
			case 59:
				fhlmcCommitmentLetter.setSecurityAmount(contentArray[i]);
				break;
			case 60:
				fhlmcCommitmentLetter.setSecurityCoupon(contentArray[i]);
				break;
			case 61:
				fhlmcCommitmentLetter.setSecurityProduct(contentArray[i]);
				break;
			case 62:
				fhlmcCommitmentLetter.setSellerIdentifier(contentArray[i]);
				break;
			case 63:
				fhlmcCommitmentLetter.setServicingFee(parseBigDecimal(contentArray[i]));
				break;
			case 64:
				fhlmcCommitmentLetter.setServicingOptionType(contentArray[i]);
				break;
			case 65:
				fhlmcCommitmentLetter.setSettlementCycleDays(contentArray[i]);
				break;
			case 66:
				fhlmcCommitmentLetter.setTotalUPB(contentArray[i]);
				break;
			}
		}
		return fhlmcCommitmentLetter;
	}
	
	public Double parseDouble(String value){
		if (!value.isEmpty()){
			return Double.parseDouble(value);
		}
		else{
			return null;
		}
	}
	
	public int parseInteger(String value){
		int newValue = -1;
		if (value != null || !value.isEmpty()){
			if (value.matches("[0-9]+")){
				newValue = Integer.parseInt(value);
			}
		}
		return newValue;
	}
	
	public BigDecimal parseBigDecimal(String value){
		BigDecimal newValue;
		if (value == null || value.isEmpty()){
			newValue = new BigDecimal("0");
		}
		try{
			newValue = new BigDecimal(value);
		}catch(Exception e){
			newValue = new BigDecimal("0");
		}
		return newValue;
	}
	
	public boolean parseBoolean(String value){
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1")){
			return true;
		}
		else{
			return false;
		}
	}

	public Date parseDate(String dateToParse){
		Date date = null;
		if (!dateToParse.isEmpty()){
			if (dateToParse.length() > 11){
				try {
					date = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.ENGLISH).parse(dateToParse);
				} catch (ParseException e) {
					System.out.println("Invalid Date: " +dateToParse);
				}
			}
			else{
				try {
					date = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH).parse(dateToParse);
				} catch (ParseException e) {
					System.out.println("Invalid Date: " +dateToParse);
				}
			}
		}
		
		return date;
	}
	
	public AgencyCommitmentLetter readCSVFile(File file) throws Exception{

		AgencyCommitmentLetter agencyCommitmentLetter = new AgencyCommitmentLetter();

		try {
//			String content = FileUtils.readFileToString(file);

			agencyCommitmentLetter = readCSVFile2(file);

		// return agencyCommitmentLetter;

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		return agencyCommitmentLetter;
	}

	
	   private String formatFile(File file) throws Exception {
		   DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
		   AgencyCommitmentLetter agencyCommitmentLetter = new AgencyCommitmentLetter();
		   agencyCommitmentLetter = readCSVFile2(file);
		   if (agencyCommitmentLetter.getLenderName().contains("Access National Bank")){
			   agencyCommitmentLetter.setLenderName("Access National Mortgage");
		   }
		   else if(agencyCommitmentLetter.getLenderName().contains("Iberia Bank")){
			   agencyCommitmentLetter.setLenderName("Iberiabank Mortgage Company");
		   }
		   else if(agencyCommitmentLetter.getLenderName().contains("M/I Financial Corp")){
			   agencyCommitmentLetter.setLenderName("MI Homes");
		   }
		   String formattedFile = "\"Lender #:\",\"" +agencyCommitmentLetter.getLenderNum();						
		   formattedFile += "\",\"Lender Name:\",\"" + agencyCommitmentLetter.getLenderName() +"\"\n";				
		   formattedFile += "\"Fannie Mae's Commitment ID:\",\"" + agencyCommitmentLetter.getAgencyCommitmentID();
		   formattedFile += "\",\"Lender's Commitment ID:\",\"" +agencyCommitmentLetter.getLenderCommitmentID()+ "\"\n";
		   formattedFile += "\"Source:\",\"" +agencyCommitmentLetter.getSource();
		   formattedFile += "\",\"Commitment Date:\",\"" +df.format(agencyCommitmentLetter.getCommitmentDate())+ "\"\n";
		   formattedFile += "\"Contact:\",\"" + agencyCommitmentLetter.getContact();
		   formattedFile += "\",\"Expiration Date:\",\"" +df.format(agencyCommitmentLetter.getExpirationDate())+ "\"\n";
		   formattedFile += "\"Delivery:\"\n";
		   formattedFile += "\"Original Commitment Amount:\",\"" + agencyCommitmentLetter.getOrigCommitmentAmt();
		   formattedFile += "\",\"Low Tolerance Amount:\",\"" +agencyCommitmentLetter.getLowToleranceAmt()+ "\"\n";
		   formattedFile += "\"Current Commitment Amount:\",\"" + agencyCommitmentLetter.getCurrentCommitmentAmt();
		   formattedFile += "\",\"High Tolerance Amount:\",\"" +agencyCommitmentLetter.getHighToleranceAmt()+ "\"\n";    
		   formattedFile += "\"Purchased Amount:\",\"" + agencyCommitmentLetter.getPurchasedAmt();
		   formattedFile += "\",\"Maximum Over-delivery Amount:\",\"" +agencyCommitmentLetter.getMaxOverDeliveryAmt()+ "\"\n";  
		   formattedFile += "\"Pending Purchase:\",\"" + agencyCommitmentLetter.getPendingPurchase();
		   formattedFile += "\",\"HBL Cap%:\",\"" +agencyCommitmentLetter.getHBLCap()+ "\"\n";
		   formattedFile += "\"Balance:\",\"" +agencyCommitmentLetter.getBalance();
		   formattedFile += "\",\"Maximum Allowable HBL Delivery:\",\"" +agencyCommitmentLetter.getMaxAllowableHBLDelivery()+ "\"\n";
		   formattedFile += "\"HBL Purchased Amount:\",\"" +agencyCommitmentLetter.getHBLPurchasedAmt()+ "\"\n";		  
		   formattedFile += "\"Pass-Through Rate:\",\",\"Price\",\"Net Yield\"\n"; 
		   formattedFile += "\""+agencyCommitmentLetter.getPassThroughRateHigh()+"\",\"" +agencyCommitmentLetter.getPriceHigh()+"\",\""+agencyCommitmentLetter.getNetYieldHigh()+"\"\n";
		   formattedFile += "\""+agencyCommitmentLetter.getPassThroughRateLow()+"\",\"" +agencyCommitmentLetter.getPriceLow()+"\",\""+agencyCommitmentLetter.getNetYieldLow()+"\"\n";
		   formattedFile += "\""+agencyCommitmentLetter.getPassThroughRateLow()+"\",\"" +agencyCommitmentLetter.getPriceLow()+"\",\""+agencyCommitmentLetter.getNetYieldLow()+"\"\n";
		   formattedFile += "\""+agencyCommitmentLetter.getPassThroughRateLow()+"\",\"" +agencyCommitmentLetter.getPriceLow()+"\",\""+agencyCommitmentLetter.getNetYieldLow()+"\"\n";
		   formattedFile += "\""+agencyCommitmentLetter.getPassThroughRateLow()+"\",\"" +agencyCommitmentLetter.getPriceLow()+"\",\""+agencyCommitmentLetter.getNetYieldLow()+"\"\n";
		   formattedFile += "\"Commitment Details:\"\n";
		   formattedFile += "\"Master Agreement ID:\",\"" +agencyCommitmentLetter.getMasterAgreementID()+"\"\n";
		   formattedFile += "\"Commitment Period:\",\"" +agencyCommitmentLetter.getCommitmentPeriod();
		   formattedFile += "\",\"Base Servicing Fee:\",\"" +agencyCommitmentLetter.getBaseServicingFee()+ "\",\"BPS\"\n";
		   formattedFile += "\"Pricing Option:\",\"" +agencyCommitmentLetter.getPricingOption();
		   formattedFile += "\",\"LPMI:\",\"" +agencyCommitmentLetter.getLPMI()+ "\"\n";
		   formattedFile += "\"Minimum Pass-through Rate:\",\"" +agencyCommitmentLetter.getMinPassThroughRate();
		   formattedFile += "\",\"Total Servicing Fee:\",\"" +agencyCommitmentLetter.getTotalServicingFee()+ "\",\"BPS\"\n";
		   formattedFile += "\"Required Net Yield:\",\"" +agencyCommitmentLetter.getRequiredNetYield()+"\"\n";
		   formattedFile += "\"LLPA Applicable:\",\"" +agencyCommitmentLetter.getLLPAApplicable()+"\"\n"; 
		   formattedFile += "\"Product Description:\"\n";
		   formattedFile += "\"Product Name:\",\"" +agencyCommitmentLetter.getProductName()+"\"\n";
		   formattedFile += "\"Product Favorite Name:\",\"" +agencyCommitmentLetter.getProductFavoriteName()+"\"\n";
		   formattedFile += "\"Amortization Type:\",\"" +agencyCommitmentLetter.getAmortizationType()+"\",";
		   formattedFile += "\"Amortization Term:\",\"" +agencyCommitmentLetter.getAmortizationTerm()+"\"\n";
		   formattedFile += "\"Loan Type:\",\"" +agencyCommitmentLetter.getLoanType()+"\",";
		   formattedFile += "\"Loan Term:\",\"" +agencyCommitmentLetter.getLoanTerm()+"\"\n";
		   formattedFile += "\"Lien Type:\",\"" +agencyCommitmentLetter.getLienType()+"\",";
		   formattedFile += "\"Remittance Type:\",\"" +agencyCommitmentLetter.getRemittanceType()+"\"\n";
		   
		return formattedFile;
	}

	private void prepareAgencyCommitmentLetter(AgencyCommitmentLetter agencyCommitmentLetter, ArrayList<String> agencyCommitAL) throws Exception {
	       
		   String[] agencyCommit = agencyCommitAL.toArray(new String[agencyCommitAL.size()]);
		   
//		   System.out.println("\n\nAGENCY COMMIT SIZE: " + agencyCommit.length+"\n\n");
		   
//		   for (int i=0; i<agencyCommit.length; i++){
//			   System.out.println(i+ ": " +agencyCommit[i]);
//		   }

	     //   System.out.println(" agencyCommit "+agencyCommitAL);
	        
	        agencyCommitmentLetter.setLenderNum(getIntegerValue(agencyCommit[0].replaceAll("-", "")));
	        agencyCommitmentLetter.setLenderName(getStringValue(agencyCommit[1]));
	        agencyCommitmentLetter.setAgencyCommitmentID(getStringValue(agencyCommit[2]));
	        agencyCommitmentLetter.setLenderCommitmentID(getIntegerValue(agencyCommit[3]));
	        agencyCommitmentLetter.setSource(getStringValue(agencyCommit[4]));
	        agencyCommitmentLetter.setCommitmentDate(getDateValue(agencyCommit[5]));

	        agencyCommitmentLetter.setDataDeliveryDate(agencyCommitmentLetter.getCommitmentDate());

	        agencyCommitmentLetter.setContact(getStringValue(agencyCommit[6]));
	        agencyCommitmentLetter.setExpirationDate(getDateValue(agencyCommit[7]));
	        	        
	        agencyCommitmentLetter.setTargetFundDate(getTargetFundDate(agencyCommitmentLetter.getExpirationDate()));
	        agencyCommitmentLetter.setOrigCommitmentAmt(getBigDecimalValue(agencyCommit[9]));
	        agencyCommitmentLetter.setLowToleranceAmt(getBigDecimalValue(agencyCommit[10]));
	        
	        agencyCommitmentLetter.setCurrentCommitmentAmt(getBigDecimalValue(agencyCommit[11]));
	        agencyCommitmentLetter.setHighToleranceAmt(getBigDecimalValue(agencyCommit[12]));
	        agencyCommitmentLetter.setPurchasedAmt(getBigDecimalValue(agencyCommit[13]));
	        agencyCommitmentLetter.setMaxOverDeliveryAmt(getBigDecimalValue(agencyCommit[14]));
	        agencyCommitmentLetter.setPendingPurchase(getBigDecimalValue(agencyCommit[15]));
	        agencyCommitmentLetter.setHBLCap(getStringValue(agencyCommit[16]));
	        agencyCommitmentLetter.setBalance(getBigDecimalValue(agencyCommit[17]));
	        agencyCommitmentLetter.setMaxAllowableHBLDelivery(getStringValue(agencyCommit[18]));
//	        System.out.println("\n\nHBL PURCHASE AMT: " +agencyCommitmentLetter.getHBLPurchasedAmt()+"\n\n");
//	        agencyCommitmentLetter.setHBLPurchasedAmt(getBigDecimalValue(agencyCommit[]));
	        agencyCommitmentLetter.setPassThroughRateHigh(getDoubleValue(agencyCommit[19]));
	        
	        agencyCommitmentLetter.setPriceHigh(getDoubleValue(agencyCommit[20]));
	        agencyCommitmentLetter.setNetYieldHigh(getDoubleValue(agencyCommit[21])); 
	        agencyCommitmentLetter.setPassThroughRateLow(getDoubleValue(agencyCommit[22]));
	        agencyCommitmentLetter.setPriceLow(getDoubleValue(agencyCommit[23]));
//	        System.out.println("\nNET YIELD LOW: " + agencyCommit[24]);
	        agencyCommitmentLetter.setNetYieldLow(getDoubleValue(agencyCommit[24]));
	        agencyCommitmentLetter.setMasterAgreementID(getStringValue(agencyCommit[25]));
	        agencyCommitmentLetter.setCommitmentPeriod(getStringValue(agencyCommit[26]));
	        agencyCommitmentLetter.setBaseServicingFee(getDoubleValue(agencyCommit[27]));  
	        agencyCommitmentLetter.setPricingOption(getStringValue(agencyCommit[28]));
	        agencyCommitmentLetter.setLPMI(getDoubleValue(agencyCommit[29]));
	        
	        agencyCommitmentLetter.setMinPassThroughRate(getDoubleValue(agencyCommit[30]));
	        agencyCommitmentLetter.setTotalServicingFee(getDoubleValue(agencyCommit[31]));
	        agencyCommitmentLetter.setRequiredNetYield(getDoubleValue(agencyCommit[32]));
	        agencyCommitmentLetter.setLLPAApplicable(getBooleanValue(agencyCommit[33]));
//	        agencyCommitmentLetter.setProductDescription(getStringValue(agencyCommit[34]));
//	        System.out.println("\nLINE 34: " + agencyCommit[33]+ "\n");
//	        System.out.println("LINE 35: " + agencyCommit[34]+ "\n");
//	        System.out.println("LINE 36: " + agencyCommit[35]+ "\n");
	        agencyCommitmentLetter.setProductName(getStringValue(agencyCommit[35]));
	        agencyCommitmentLetter.setProductFavoriteName(getStringValue(agencyCommit[36]));
	        agencyCommitmentLetter.setAmortizationType(getStringValue(agencyCommit[38]));
	        agencyCommitmentLetter.setAmortizationTerm(getStringValue(agencyCommit[39]));
	        agencyCommitmentLetter.setLoanType(getStringValue(agencyCommit[40]));
	        
	        agencyCommitmentLetter.setLoanTerm(getStringValue(agencyCommit[41]));
	        agencyCommitmentLetter.setLienType(getStringValue(agencyCommit[42]));
	        agencyCommitmentLetter.setRemittanceType(getStringValue(agencyCommit[43]));
	    }
	   
	 //TargetFundDt = CMC Commitment Expiration Date + 7 Days excluding holidays
	// CMC Commitment Expiration Date == Agency Commitment Expiration Date
	private Date getTargetFundDate(Date commitmentExprDate){
		Date tempDate = null;
		if (!(commitmentExprDate == null)){
			tempDate = DateUtil.addDays(commitmentExprDate, 8); // 7 calendar days
		}
		return tempDate;
	}

	 //DataDeliveryDate = CMC Commitment  Date + 3 Days excluding holidays
	private Date getDataDeliveryDate(Date commitmentDate){
		Date tempDate = null;
		if (!(commitmentDate == null)){
			tempDate = DateUtil.addDays(commitmentDate, 4); // 3 calendar days
		}
		return tempDate;
	}

	
	private Integer getIntegerValue(String value) {
		Integer tempVal = null;
		if (value.equalsIgnoreCase("none")){
			value = "";
		}
		else if (value.contains("days")) {
			value = value.replaceAll(" days", "");
		}
		if (!(value == null)){
			try {
				tempVal = Integer.parseInt(value);
			} catch (NumberFormatException nfe) {
				tempVal = null;
			}
		}
		return tempVal;
	}

	private String getStringValue(String value) {
		if (value == null){
			value = "";
		}
		else if(value.equalsIgnoreCase("none")){
			value = "";
		}
		else if (value.equalsIgnoreCase("null")) {
            value = "";
        }
		else if (value.equalsIgnoreCase("n/a")) {
            value = "";
        }
		else if (value.equalsIgnoreCase("na")){
        	value = "";
        }
		else if (value == ""){
			value = "";
		}
        return value;
    }

    private Double getDoubleValue(String value) {
    	Double tempVal = null;
    	if (!(value == null)){
    		if (value.equalsIgnoreCase("none")){
				tempVal = 0.00;
			}
    		else{
				try {
					tempVal = Double.valueOf(value);
				} catch (NumberFormatException nfe) {
					tempVal = 0.00;
				}
    		}
			
		}
    	else{
    		tempVal = 0.00;
    	}
        return Double.valueOf(tempVal);
    }

    private BigDecimal getBigDecimalValue(String value) {
    	BigDecimal tempBD = null;
//    	System.out.println("\n\nBIG DECIMAL VALUE: " + value+"\n\n");
    	if (!(value == null)){
	        if (value.equalsIgnoreCase("n/a") || value.equalsIgnoreCase("na")){
	        	tempBD =  new BigDecimal(0.00);
	        }
	        else{
	        	tempBD = new BigDecimal(value);
	        }
    	}
    	return tempBD;
    }

    private Boolean getBooleanValue(String value) {
    	Boolean tempVal = null;
    	if (!(value == null)){
	        if (value.equalsIgnoreCase("yes")) {
	            value = "true";
	        } else {
	            value = "false";
	        }
	        tempVal = Boolean.parseBoolean(value);
    	}
        return tempVal;
    }

    private Date getDateValue(String value) throws Exception {
    	Date tempDate = null;
    	if (!(value == null)){
    		if (value.contains("/")){
	    		//Check for 4-digit year
	    		String checkDate = "";
	        	String []checkYear = value.split("/");
	        	if(checkYear[2].length() < 4){
	        		checkDate = checkYear[0] +"/"+ checkYear[1] +"/20"+ checkYear[2];
	        		value = checkDate;
	//        		System.out.println("\n\nFIXED DATE: " + checkDate);
	        	}
    		}
    		if (value.contains("-")){
    			//Check for 4-digit year
	    		String checkDate = "";
	        	String []checkYear = value.split("-");
	        	if(checkYear[0].length() == 4){
	        		checkDate = checkYear[1] +"/"+ checkYear[2] +"/"+ checkYear[0];
	        		value = checkDate;
	        		System.out.println("\n\nFIXED DATE: " + checkDate);
	        	}
    		}
		        String newFormat = "MM/dd/yyyy";
		
		        SimpleDateFormat sdf = new SimpleDateFormat(newFormat);
		
		        tempDate = sdf.parse(value);
    	}
        return tempDate;
    }
    


	public String conv(String x) {
		if (x.equals("")) {
			x = null;
		}
		if (x.contains("$")) {
			x = x.substring(1);
			x = x.replaceAll(",", "");
			// System.out.println(x);
		}

		// Conver any dat to format MM/DD/YYYY
		int count = StringUtils.countMatches(x, "/"); // count # of forward
														// slashes
		if (count == 2) { // identify date
			x = x.split("\\s")[0]; // remove extended data from date
			if ((x.split("/")[2]).length() < 4) { // check if the year is < 4
													// characters
				x = x.split("/")[0] + "/" + x.split("/")[1] + "/" + "20"
						+ x.split("/")[2]; // reshuffle the date
			}
		}
		return x;
	}
	
	public AgencyCommitmentLetter readCSVFile2(File file) throws Exception{

		AgencyCommitmentLetter agencyCommitmentLetter = new AgencyCommitmentLetter();
		String fileContent;
		//Handle xls file
		String fileName = file.getName();
		if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")){
			DataConversionUtil dataConversionUtil = new DataConversionUtil();
			fileContent = dataConversionUtil.getExcelToCSVString(file);
		}
		else{
			fileContent = FileUtils.readFileToString(file);
//			System.out.println(fileContent);
		}

		String[] contentArray = fileContent.split("\\n");
		
		
		for (int i = 0; i < contentArray.length; i++) {
			String[] valueArray = contentArray[i].split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
			for (int j = 0; j < valueArray.length; j++) {
//				System.out.println(j+": " +valueArray[j].replaceAll("\"", ""));
				
				//Lender #
				if (valueArray[j].contains("Lender #")){
					agencyCommitmentLetter.setLenderNum(Integer.parseInt(valueArray[j+1].replaceAll("-", "").replaceAll("\"", "")));
					System.out.println(agencyCommitmentLetter.getLenderNum());
				}
				
				//Lender Name
				else if (valueArray[j].contains("Lender Name")){
//					agencyCommitmentLetter.setLenderName(valueArray[j+1].replaceAll("\"", ""));
					//Get lender name from lender #
					PatronCompanyDAO patronCoDAO = new PatronCompanyDAO();
					agencyCommitmentLetter.setLenderName(patronCoDAO.getPatronCompanyByLenderNum(agencyCommitmentLetter.getLenderNum()));
					System.out.println("\nLender Name: " + agencyCommitmentLetter.getLenderName());
				}
//				else if (valueArray[j].contains("Lender Name")){
////					agencyCommitmentLetter.setLenderName(valueArray[j+1].replaceAll("\"", ""));
//					//Get lender name from lender #
//					PatronCompanyDAO patronCoDAO = new PatronCompanyDAO();
//					if (patronCoDAO.getPatronCompanyByLenderNum(agencyCommitmentLetter.getLenderNum()).isEmpty()){
//						RefPatronCoDAO refPatronCoDAO = new RefPatronCoDAO();
//						agencyCommitmentLetter.setLenderName(refPatronCoDAO.getPatronCompanyByLenderNum(agencyCommitmentLetter.getLenderNum()));
//					}
//					else{
//						agencyCommitmentLetter.setLenderName(patronCoDAO.getPatronCompanyByLenderNum(agencyCommitmentLetter.getLenderNum()));
//					}
//					System.out.println("\nLender Name: " + agencyCommitmentLetter.getLenderName());
//				}
				
				//Fannie Mae's Commitment ID
				else if (valueArray[j].contains("Fannie Mae's Commitment ID")){
					agencyCommitmentLetter.setAgencyCommitmentID(valueArray[j+1].replaceAll("\"", "").replaceAll(".0", ""));
					System.out.println(agencyCommitmentLetter.getAgencyCommitmentID());
				}
				
				//Lender's Commitment ID
				else if (valueArray[j].contains("Lender's Commitment ID")){
					System.out.println("\nBEFORE COMMITMENT ID: " + valueArray[j+1]+ "\n");
					if (isInteger(valueArray[j+1].replaceAll("\"", ""))){
						agencyCommitmentLetter.setLenderCommitmentID(Integer.parseInt(valueArray[j+1].replaceAll("\"", "")));
					}
					System.out.println("\nLender's Commitment ID" + agencyCommitmentLetter.getLenderCommitmentID());
				}
				
				//Source
				else if (valueArray[j].contains("Source")){
					agencyCommitmentLetter.setSource(valueArray[j+1].replaceAll("\"", ""));
					System.out.println(agencyCommitmentLetter.getSource());
				}
				
				//Commitment Date
				else if (valueArray[j].contains("Commitment Date")){
					agencyCommitmentLetter.setCommitmentDate(getDateValue(DateUtil.formatXLSXDate(valueArray[j+1].replaceAll("\"", ""))));
					agencyCommitmentLetter.setDataDeliveryDate(agencyCommitmentLetter.getCommitmentDate());
					System.out.println(agencyCommitmentLetter.getCommitmentDate());
				}
				
				//Contact
				else if (valueArray[j].contains("Contact")){
					agencyCommitmentLetter.setContact(valueArray[j+1].replaceAll("\"", ""));
					System.out.println("\nContact" +agencyCommitmentLetter.getContact());
				}
				
				//Expiration Date
				else if (valueArray[j].contains("Expiration Date")){
					agencyCommitmentLetter.setExpirationDate(getDateValue(DateUtil.formatXLSXDate(valueArray[j+1].replaceAll("\"", ""))));
					System.out.println("\nExpiration Date: " +agencyCommitmentLetter.getExpirationDate());
					agencyCommitmentLetter.setTargetFundDate(getTargetFundDate(agencyCommitmentLetter.getExpirationDate()));
					System.out.println("\n\nTarget Fund Date: " +agencyCommitmentLetter.getTargetFundDate()+ "\n\n");
					
				}

				//Target Fund Date
//				if (agencyCommitmentLetter.getExpirationDate() != null){
//					System.out.println("\n\nExpiration Date (TARGET): " + agencyCommitmentLetter.getExpirationDate());
//					if (agencyCommitmentLetter.getTargetFundDate() == null){
//						System.out.println("\nTarget Fund Date: " +agencyCommitmentLetter.getTargetFundDate());
//						agencyCommitmentLetter.setTargetFundDate(getTargetFundDate(agencyCommitmentLetter.getExpirationDate()));
//						System.out.println("\n\nTarget Fund Date: " +agencyCommitmentLetter.getTargetFundDate()+ "\n\n");
//					}
//				}
				
				//Original Commitment Amount
				else if (valueArray[j].contains("Original Commitment Amount")){
					agencyCommitmentLetter.setOrigCommitmentAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println(agencyCommitmentLetter.getOrigCommitmentAmt());
				}
				
				//Low Tolerance Amount
				else if (valueArray[j].contains("Low Tolerance")){
					agencyCommitmentLetter.setLowToleranceAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n" +agencyCommitmentLetter.getLowToleranceAmt());
				}
				
				//Current Commitment Amount
				else if (valueArray[j].contains("Current Commitment Amount")){
					agencyCommitmentLetter.setCurrentCommitmentAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getCurrentCommitmentAmt());
				}
				
				//High Tolerance Amount
				else if (valueArray[j].contains("High Tolerance Amount")){
					agencyCommitmentLetter.setHighToleranceAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getHighToleranceAmt());
				}
				
				//Purchased Amount
				else if (valueArray[j].contains("Purchased Amount")){
					if (!valueArray[j].contains("HBL")){
						agencyCommitmentLetter.setPurchasedAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
						System.out.println("\n\n>" +agencyCommitmentLetter.getPurchasedAmt());
					}
					
					//HBL Purchased Amount
					else{
						System.out.println("\n\n>" +valueArray[j+1].replaceAll("\\$", ""));
						agencyCommitmentLetter.setHBLPurchasedAmt(getBigDecimalValue(valueArray[j+1].replaceAll("\"", "").replaceAll("\\$", "")));
						System.out.println("\n\n>" +agencyCommitmentLetter.getHBLPurchasedAmt());
					}
				}
				
				//Maximum over-delivery amount
				else if (valueArray[j].contains("Maximum Over")){
					agencyCommitmentLetter.setMaxOverDeliveryAmt(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getMaxOverDeliveryAmt());
				}
				
				//Pending Purchase
				else if (valueArray[j].contains("Pending Purchase")){
					agencyCommitmentLetter.setPendingPurchase(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getPendingPurchase());
				}
				
				//HBL Cap%
				else if (valueArray[j].contains("HBL Cap")){
					agencyCommitmentLetter.setHBLCap(getStringValue(valueArray[j+1].replaceAll("\"","")));
					System.out.println(agencyCommitmentLetter.getHBLCap());
				}
				
				//Balance
				else if (valueArray[j].contains("Balance:")){
					agencyCommitmentLetter.setBalance(getBigDecimalValue(valueArray[j+1].replaceAll("[^\\d.]", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getBalance());
				}
				
				//Maximum Allowable HBL Delivery
				else if (valueArray[j].contains("Maximum Allowable HBL")){
					agencyCommitmentLetter.setMaxAllowableHBLDelivery(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getMaxAllowableHBLDelivery());
				}
				
				//Pass-Through Rate - Price - Netyield
				else if (valueArray[j].contains("Pass-Through Rate")){
					i += 1;
					String[] tempValueArrayHigh = contentArray[i].split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
					for (int k=0; k<tempValueArrayHigh.length; k++){
						System.out.println(tempValueArrayHigh[k].replaceAll("\"", ""));
						switch(k){
						case 0:
							agencyCommitmentLetter.setPassThroughRateHigh(getDoubleValue(tempValueArrayHigh[k].replaceAll("\"", "")));
							break;
						case 1:
							agencyCommitmentLetter.setPriceHigh(getDoubleValue(tempValueArrayHigh[k].replaceAll("\"", "")));
							break;
						case 2:
							agencyCommitmentLetter.setNetYieldHigh(getDoubleValue(tempValueArrayHigh[k].replaceAll("\"", "")));
							break;
						}
					}
					
					System.out.println("\n\n>" +agencyCommitmentLetter.getPassThroughRateHigh());
					System.out.println("\n\n>" +agencyCommitmentLetter.getPriceHigh());
					System.out.println("\n\n>" +agencyCommitmentLetter.getNetYieldHigh());
					
					i += 4;
					String[] tempValueArray = contentArray[i].split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
					for (int k=0; k<tempValueArray.length; k++){
						System.out.println(tempValueArray[k].replaceAll("\"", ""));
						switch(k){
						case 0:
							agencyCommitmentLetter.setPassThroughRateLow(getDoubleValue(tempValueArray[k].replaceAll("\"", "")));
							break;
						case 1:
							agencyCommitmentLetter.setPriceLow(getDoubleValue(tempValueArray[k].replaceAll("\"", "")));
							break;
						case 2:
							agencyCommitmentLetter.setNetYieldLow(getDoubleValue(tempValueArray[k].replaceAll("\"", "")));
							break;
						}
					}
					
					System.out.println("\n\n>" +agencyCommitmentLetter.getPassThroughRateLow());
					System.out.println("\n\n>" +agencyCommitmentLetter.getPriceLow());
					System.out.println("\n\n>" +agencyCommitmentLetter.getNetYieldLow());
				}
				
				//Master Agreement ID
				else if (valueArray[j].contains("Master Agreement ID")){
					agencyCommitmentLetter.setMasterAgreementID(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getMasterAgreementID());
				}
				
				//Commitment Period
				else if (valueArray[j].contains("Commitment Period")){
					agencyCommitmentLetter.setCommitmentPeriod(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getCommitmentPeriod());
				}
				
				//Base Servicing Fee
				else if (valueArray[j].contains("Base Servicing Fee")){
					agencyCommitmentLetter.setBaseServicingFee(getDoubleValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getBaseServicingFee());
				}
				
				//Pricing Option
				else if (valueArray[j].contains("Pricing Option")){
					agencyCommitmentLetter.setPricingOption(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getPricingOption());
				}
				
				//LPMI
				else if (valueArray[j].contains("LPMI")){
					agencyCommitmentLetter.setLPMI(getDoubleValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getLPMI());
				}
				
				//Minimum Pass-Through Rate
				else if (valueArray[j].contains("Minimum Pass")){
					agencyCommitmentLetter.setMinPassThroughRate(getDoubleValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getMinPassThroughRate());
				}
				
				//Total Servicing Fee
				else if (valueArray[j].contains("Total Servicing Fee")){
					agencyCommitmentLetter.setTotalServicingFee(getDoubleValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getTotalServicingFee());
				}
				
				//Required Net Yield
				else if (valueArray[j].contains("Required Net Yield")){
					agencyCommitmentLetter.setRequiredNetYield(getDoubleValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getRequiredNetYield());
				}
				
				//LLPA Application
				else if (valueArray[j].contains("LLPA Applicable")){
					agencyCommitmentLetter.setLLPAApplicable(getBooleanValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getLLPAApplicable());
				}
				
				//Product Name
				else if (valueArray[j].contains("Product Name")){
					agencyCommitmentLetter.setProductName(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getProductName());
				}
				
				//Product Favorite Name
				else if (valueArray[j].contains("Product Favorite Name")){
					agencyCommitmentLetter.setProductFavoriteName(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getProductFavoriteName());
				}
				
				//Amortization Type
				else if (valueArray[j].contains("Amortization Type")){
					agencyCommitmentLetter.setAmortizationType(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getAmortizationType());
				}
				
				//Amortization Term
				else if (valueArray[j].contains("Amortization Term")){
					agencyCommitmentLetter.setAmortizationTerm(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getAmortizationTerm());
				}
				
				//Loan Type
				else if (valueArray[j].contains("Loan Type")){
					agencyCommitmentLetter.setLoanType(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getLoanType());
				}
				
				//Loan Term
				else if (valueArray[j].contains("Loan Term")){
					agencyCommitmentLetter.setLoanTerm(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getLoanTerm());
				}
				
				//Lien Type
				else if (valueArray[j].contains("Lien Type")){
					agencyCommitmentLetter.setLienType(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getLienType());
				}
				
				//Remittance Type
				else if (valueArray[j].contains("Remittance Type")){
					agencyCommitmentLetter.setRemittanceType(getStringValue(valueArray[j+1].replaceAll("\"", "")));
					System.out.println("\n\n>" +agencyCommitmentLetter.getRemittanceType());
				}
			}
		}

		return agencyCommitmentLetter;
	}
	
	public static boolean isInteger(String s) {
	    try { 
	        Integer.parseInt(s); 
	    } catch(NumberFormatException e) { 
	        return false; 
	    }
	    // only got here if we didn't return false
	    return true;
	}
}

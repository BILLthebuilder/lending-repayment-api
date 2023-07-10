package com.lending.Service;

import com.lending.Util.SendSms;
import com.lending.dto.*;
import com.lending.mappers.LoanMapper;
import com.lending.repository.LoanRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;



import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;

    private final LoanMapper loanMapper;



    @Transactional
    public ResponseEntity<GenericResponse> createLoanRequest(CreateLoanRequest request, Errors errors) {
        boolean created = false;
        GenericResponse response = null;
        if (errors.hasFieldErrors()) {
            FieldError fieldError = errors.getFieldError();
            response = new GenericResponse(fieldError.getDefaultMessage(), "FAILED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
        try {
            var loan = loanMapper.toLoan(request);
            loanRepository.save(loan);
            //SendSms.sendSms(request.phoneNumber(),"Loan request processed succesfully");
            created = true;
            response = new GenericResponse("Loan created successfully","SUCCESS");
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            created = false;
            log.error("Error creating loan=%s", e);
            response = new GenericResponse(e.getMessage(), "FAILED");
            return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public ResponseEntity<GenericResponse> topupLoan(TopupLoanRequest request, Errors errors) {
        boolean status = false;
        GenericResponse response;

        if (errors.hasFieldErrors()) {
            FieldError fieldError = errors.getFieldError();
            response = new GenericResponse(fieldError.getDefaultMessage(), "FAILED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
        var loan = loanRepository.findById(request.id());

        BigDecimal currentAmount = loan.getAmount();
        try {
            if (loanExists(request.id())) {
                loan.setAmount(currentAmount.add(request.topupAmount()));
                loanRepository.save(loan);
                //SendSms.sendSms(request.phoneNumber(),"Loan topup request processed succesfully");
                status = true;
                response = new GenericResponse("Loan topup successful","SUCCESS");
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                log.error("Get a loan first to qualify for a topup");
                response = new GenericResponse("Loan topup failed", "FAILED");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Error toping up loan=%s", e);
            response = new GenericResponse(e.getMessage(), "FAILED");
            return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public ResponseEntity<GenericResponse> repayLoan(RepaymentRequest request, Errors errors) {
        boolean status = false;
        GenericResponse response;

        if (errors.hasFieldErrors()) {
            FieldError fieldError = errors.getFieldError();
            response = new GenericResponse(fieldError.getDefaultMessage(), "FAILED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
        var loan = loanRepository.findById(request.id());
        var repaymentAmont = request.amount();
        var currentLoanAmount = loan.getAmount();
        var remainingAmount = currentLoanAmount.subtract(repaymentAmont);
        var repayMentStatus = "";
        if(remainingAmount.compareTo(BigDecimal.ZERO)>0){
           repayMentStatus = "1"; //Fully repaid
        }else {
            repayMentStatus = "2"; // partially repaid
        }
        try {
            if (loanExists(request.id())) {
                loan.setAmount(currentLoanAmount.subtract(repaymentAmont));
                loan.setRepaymentStatus(repayMentStatus);
                loanRepository.save(loan);
                status = true;
                response = new GenericResponse("Loan repayment successful","SUCCESS");
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                log.error("Get a loan first to allow repayment");
                response = new GenericResponse("Loan repayment failed", "FAILED");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Error repaying loan=%s", e);
            response = new GenericResponse(e.getMessage(), "FAILED");
            return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public ResponseEntity<GenericResponse> clearOldLoans(ClearOldLoansRequest request, Errors errors) {
        boolean status = false;
        GenericResponse response;

        if (errors.hasFieldErrors()) {
            FieldError fieldError = errors.getFieldError();
            response = new GenericResponse(fieldError.getDefaultMessage(), "FAILED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
        var loan = loanRepository.findById(request.id());
        var getCurrentLoanDate = loan.getDateCreated();
        var calendar = Calendar.getInstance();
        calendar.setTime(getCurrentLoanDate);
        calendar.add(Calendar.MONTH, 6);

        var sixMonthsLater = calendar.getTime();

        // Check repayment status(-1 is for defaulted loans)
        var repaymentStatus = loan.getRepaymentStatus();

        try {
            if (loanExists(request.id())) {
                //Check if loan is 6 months old or past 6 months
                boolean isPastOrEqual = getCurrentLoanDate.before(sixMonthsLater) || getCurrentLoanDate.equals(sixMonthsLater);
                if (isPastOrEqual || repaymentStatus.equalsIgnoreCase("-1")) {
                    loanRepository.delete(loan);
                    status = true;
                    response = new GenericResponse("Loan clearing successful","SUCCESS");
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }
            }else {
                log.error("Error clearing loan");
                response = new GenericResponse("Loan clearing failed", "FAILED");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Error clearing loan=%s", e);
            response = new GenericResponse(e.getMessage(), "FAILED");
            return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        response = new GenericResponse("ERROR", "FAILED");
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public boolean loanExists(UUID id) {
        return loanRepository.findById(id) != null;
    }
}

/*
 * CS22301 OOP - Assignment 2, Question 1
 * ATM Transaction System  (single file, no packages)
 *
 * Features : PIN verification, balance enquiry, withdrawal, deposit, mini statement
 * Concepts : try / catch / finally, throw, throws, multi-catch, re-throwing,
 *            6 user-defined checked exceptions, file I/O (try-with-resources)
 *
 * Save as : ATMTransactionSystem.java
 * Compile : javac ATMTransactionSystem.java
 * Run     : java ATMTransactionSystem
 * Demo accounts: 1001 / PIN 1234   and   1002 / PIN 4321
 * Needs JDK 17 or newer.
 */
package Question1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

class ATMTransactionSystem {

    /**
     * Base class of all user-defined ATM exceptions (checked).
     * Catching ATMException catches every ATM-related failure.
     */
    static class ATMException extends Exception {
        private static final long serialVersionUID = 1L;

        public ATMException(String message) {
            super(message);
        }
    }

    /** Thrown when the entered PIN does not match the account PIN. */
    static class InvalidPinException extends ATMException {
        private static final long serialVersionUID = 1L;

        private final int attemptsLeft;

        public InvalidPinException(int attemptsLeft) {
            super("Invalid PIN. Attempts left: " + attemptsLeft);
            this.attemptsLeft = attemptsLeft;
        }

        public int getAttemptsLeft() {
            return attemptsLeft;
        }
    }

    /** Thrown when an account is locked after too many wrong PIN attempts. */
    static class AccountLockedException extends ATMException {
        private static final long serialVersionUID = 1L;

        public AccountLockedException(String accountNumber) {
            super("Account " + accountNumber + " is locked. Please contact your bank.");
        }
    }

    /** Thrown when the account number does not exist. */
    static class InvalidAccountException extends ATMException {
        private static final long serialVersionUID = 1L;

        public InvalidAccountException(String accountNumber) {
            super("Account number '" + accountNumber + "' does not exist.");
        }
    }

    /** Thrown when the requested withdrawal is more than the available balance. */
    static class InsufficientBalanceException extends ATMException {
        private static final long serialVersionUID = 1L;

        public InsufficientBalanceException(double balance, double requested) {
            super(String.format("Insufficient balance. Available: Rs. %.2f, requested: Rs. %.2f",
                    balance, requested));
        }
    }

    /** Thrown when a transaction amount is zero, negative, non-numeric or not a multiple of 100. */
    static class InvalidAmountException extends ATMException {
        private static final long serialVersionUID = 1L;

        public InvalidAmountException(String message) {
            super(message);
        }
    }

    /** Thrown when a withdrawal would cross the daily withdrawal limit. */
    static class WithdrawalLimitExceededException extends ATMException {
        private static final long serialVersionUID = 1L;

        public WithdrawalLimitExceededException(double limit, double remaining) {
            super(String.format("Daily withdrawal limit of Rs. %.2f exceeded. You can still withdraw Rs. %.2f today.",
                    limit, remaining));
        }
    }

    /**
     * Bank account used by the ATM. All validation rules live here and are
     * reported through user-defined exceptions (declared with 'throws', raised with 'throw').
     * NOTE: the PIN is stored as plain text only to keep this assignment simple.
     */
    static class Account {
        public static final double DAILY_WITHDRAWAL_LIMIT = 20000.0;
        public static final int MAX_PIN_ATTEMPTS = 3;
        public static final double NOTE_MULTIPLE = 100.0;

        private final String accountNumber;
        private final String holderName;
        private final String pin;
        private double balance;
        private double withdrawnToday;
        private int failedAttempts;
        private boolean locked;

        public Account(String accountNumber, String holderName, String pin, double openingBalance) {
            this.accountNumber = accountNumber;
            this.holderName = holderName;
            this.pin = pin;
            this.balance = openingBalance;
        }

        public String getAccountNumber() { return accountNumber; }
        public String getHolderName()    { return holderName; }
        public double getBalance()       { return balance; }
        public boolean isLocked()        { return locked; }

        /** PIN verification: locks the account after MAX_PIN_ATTEMPTS wrong tries. */
        public void verifyPin(String enteredPin) throws InvalidPinException, AccountLockedException {
            if (locked) {
                throw new AccountLockedException(accountNumber);
            }
            if (enteredPin == null || !pin.equals(enteredPin)) {
                failedAttempts++;
                if (failedAttempts >= MAX_PIN_ATTEMPTS) {
                    locked = true;
                    throw new AccountLockedException(accountNumber);
                }
                throw new InvalidPinException(MAX_PIN_ATTEMPTS - failedAttempts);
            }
            failedAttempts = 0; // correct PIN resets the counter
        }

        public void deposit(double amount) throws InvalidAmountException {
            validateAmount(amount);
            balance += amount;
        }

        public void withdraw(double amount)
                throws InvalidAmountException, WithdrawalLimitExceededException, InsufficientBalanceException {
            validateAmount(amount);
            if (withdrawnToday + amount > DAILY_WITHDRAWAL_LIMIT) {
                throw new WithdrawalLimitExceededException(DAILY_WITHDRAWAL_LIMIT,
                        DAILY_WITHDRAWAL_LIMIT - withdrawnToday);
            }
            if (amount > balance) {
                throw new InsufficientBalanceException(balance, amount);
            }
            balance -= amount;
            withdrawnToday += amount;
        }

        private void validateAmount(double amount) throws InvalidAmountException {
            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
                throw new InvalidAmountException("Amount must be a positive number.");
            }
            if (amount % NOTE_MULTIPLE != 0) {
                throw new InvalidAmountException("Amount must be a multiple of Rs. " + (int) NOTE_MULTIPLE + ".");
            }
        }
    }

    /** File I/O: appends every transaction to a text file and reads it back for the mini statement. */
    static class TransactionLogger {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private final Path file;

        public TransactionLogger(String fileName) {
            this.file = Paths.get(fileName);
        }

        public void log(String accountNumber, String type, String details) throws IOException {
            String line = LocalDateTime.now().format(FORMAT) + " | " + accountNumber + " | " + type + " | " + details;
            // try-with-resources closes the writer automatically
            try (BufferedWriter writer = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            }
        }

        public List<String> readHistory(String accountNumber) throws IOException {
            List<String> lines = new ArrayList<>();
            if (!Files.exists(file)) {
                return lines;
            }
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("| " + accountNumber + " |")) {
                        lines.add(line);
                    }
                }
            }
            return lines;
        }
    }

    /** Business logic of the ATM: login, balance enquiry, deposit, withdrawal and statement. */
    static class ATMService {
        private final Map<String, Account> accounts = new HashMap<>();
        private final TransactionLogger logger;

        public ATMService(TransactionLogger logger) {
            this.logger = logger;
        }

        public void addAccount(Account account) {
            accounts.put(account.getAccountNumber(), account);
        }

        /** Account lookup + PIN verification. */
        public Account login(String accountNumber, String pin)
                throws InvalidAccountException, InvalidPinException, AccountLockedException {
            Account account = accounts.get(accountNumber);
            if (account == null) {
                throw new InvalidAccountException(accountNumber);
            }
            try {
                account.verifyPin(pin);
            } catch (InvalidPinException | AccountLockedException e) {
                record(accountNumber, "LOGIN_FAILED", e.getMessage());
                throw e; // re-throw so that the caller (UI) can show the message
            }
            record(accountNumber, "LOGIN", "Login successful");
            return account;
        }

        public double checkBalance(Account account) {
            record(account.getAccountNumber(), "BALANCE", String.format("Balance Rs. %.2f", account.getBalance()));
            return account.getBalance();
        }

        public void deposit(Account account, double amount) throws InvalidAmountException {
            account.deposit(amount);
            record(account.getAccountNumber(), "DEPOSIT", String.format("Rs. %.2f", amount));
        }

        public void withdraw(Account account, double amount)
                throws InvalidAmountException, WithdrawalLimitExceededException, InsufficientBalanceException {
            account.withdraw(amount);
            record(account.getAccountNumber(), "WITHDRAW", String.format("Rs. %.2f", amount));
        }

        public void logout(Account account) {
            record(account.getAccountNumber(), "LOGOUT", "Session ended");
        }

        public List<String> miniStatement(Account account) throws IOException {
            return logger.readHistory(account.getAccountNumber());
        }

        /** A logging problem must never break a bank transaction, so the IOException is handled here. */
        private void record(String accountNumber, String type, String details) {
            try {
                logger.log(accountNumber, type, details);
            } catch (IOException e) {
                System.err.println("Warning: could not write transaction log: " + e.getMessage());
            }
        }
    }

    // ======================= MAIN PROGRAM =======================

    public static void main(String[] args) {
        ATMService atm = new ATMService(new TransactionLogger("atm_transactions.log"));
        atm.addAccount(new Account("1001", "Arun Kumar", "1234", 50000));
        atm.addAccount(new Account("1002", "Priya Devi", "4321", 8000));

        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Welcome to the Java ATM ===");
        try {
            while (true) {
                System.out.print("\nEnter account number (or 'exit'): ");
                String accNo = scanner.nextLine().trim();
                if (accNo.equalsIgnoreCase("exit")) {
                    break;
                }
                System.out.print("Enter PIN: ");
                String pin = scanner.nextLine().trim();
                try {
                    Account account = atm.login(accNo, pin);
                    runSession(scanner, atm, account);
                } catch (InvalidAccountException | InvalidPinException | AccountLockedException e) {
                    System.out.println("Login failed: " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("\nInput closed.");
        } finally {
            scanner.close();
            System.out.println("Thank you for using the ATM. Goodbye!");
        }
    }

    private static void runSession(Scanner scanner, ATMService atm, Account account) {
        System.out.println("\nWelcome, " + account.getHolderName() + "!");
        try {
            boolean active = true;
            while (active) {
                System.out.println("\n1. Balance Enquiry\n2. Withdraw\n3. Deposit\n4. Mini Statement\n5. Logout");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine().trim();
                try {
                    switch (choice) {
                        case "1" -> System.out.printf("Available balance: Rs. %.2f%n", atm.checkBalance(account));
                        case "2" -> {
                            atm.withdraw(account, readAmount(scanner, "Enter amount to withdraw: "));
                            System.out.printf("Please collect your cash. Balance: Rs. %.2f%n", account.getBalance());
                        }
                        case "3" -> {
                            atm.deposit(account, readAmount(scanner, "Enter amount to deposit: "));
                            System.out.printf("Deposit successful. Balance: Rs. %.2f%n", account.getBalance());
                        }
                        case "4" -> {
                            List<String> history = atm.miniStatement(account);
                            history.forEach(System.out::println);
                        }
                        case "5" -> active = false;
                        default -> System.out.println("Invalid option '" + choice + "'. Please choose 1-5.");
                    }
                } catch (InvalidAmountException e) {
                    System.out.println("Invalid amount: " + e.getMessage());
                } catch (InsufficientBalanceException e) {
                    System.out.println("Declined: " + e.getMessage());
                } catch (WithdrawalLimitExceededException e) {
                    System.out.println("Declined: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Could not read the statement: " + e.getMessage());
                }
            }
        } finally {
            atm.logout(account); // always runs, even if the session ends because of an error
            System.out.println("Session closed. Please take your card.");
        }
    }

    /** Reads a number from the user; non-numeric text becomes an InvalidAmountException. */
    private static double readAmount(Scanner scanner, String prompt) throws InvalidAmountException {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("'" + input + "' is not a valid number.");
        }
    }
}
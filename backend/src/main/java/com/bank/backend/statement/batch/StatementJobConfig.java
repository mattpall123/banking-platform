package com.bank.backend.statement.batch;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.repository.PostingRepository;
import com.bank.backend.statement.domain.Statement;
import com.bank.backend.statement.repository.StatementRepository;
import com.bank.backend.statement.service.StatementPdfRenderer;
import com.bank.backend.statement.service.StatementStorage;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import com.bank.backend.statement.batch.BatchJobContext;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Batch job: generate a statement for every active account for a
 * given period.
 *
 * Reader   - JpaPagingItemReader → pages through Accounts, 50 at a time
 * Processor - StatementProcessor → builds + renders + stores PDF
 * Writer   - persists the Statement entities (delete-then-insert per
 *            account so re-running for the same period is safe)
 *
 * Reader/Writer use the same EntityManagerFactory so JPA caches behave well.
 *
 * The job is parametrized by 'periodYearMonth' (a string like "2026-04");
 * the processor reads it from the StepScope context.
 */
@Configuration
public class StatementJobConfig {

    private static final Logger log = LoggerFactory.getLogger(StatementJobConfig.class);
    private static final int CHUNK_SIZE = 25;

    @Bean
    public Job statementGenerationJob(
            JobRepository jobRepository,
            Step generateStatementsStep
    ) {
        return new JobBuilder("statementGenerationJob", jobRepository)
                .start(generateStatementsStep)
                .build();
    }

    @Bean
    public Step generateStatementsStep(
            JobRepository jobRepository,
            PlatformTransactionManager txMgr,
            JpaPagingItemReader<Account> accountReader,
            ItemWriter<Statement> statementWriter,
            EntityManagerFactory emf,
            AccountHolderRepository holderRepo,
            LedgerAccountRepository ledgerAccountRepo,
            PostingRepository postingRepo,
            StatementPdfRenderer renderer,
            StatementStorage storage,
            BatchJobContext context
    ) {
        var processor = new StatementProcessor(
                context,
                holderRepo,
                ledgerAccountRepo,
                postingRepo,
                renderer,
                storage
        );

        return new StepBuilder("generateStatementsStep", jobRepository)
                .<Account, Statement>chunk(CHUNK_SIZE, txMgr)
                .reader(accountReader)
                .processor(processor)
                .writer(statementWriter)
                .build();
    }

    /** Pages through every Account, 50 at a time. */
    @Bean
    public JpaPagingItemReader<Account> accountReader(EntityManagerFactory emf) {
        Map<String, Object> params = new HashMap<>();
        return new JpaPagingItemReaderBuilder<Account>()
                .name("accountReader")
                .entityManagerFactory(emf)
                .queryString("SELECT a FROM Account a WHERE a.status = com.bank.backend.account.domain.AccountStatus.ACTIVE ORDER BY a.id")
                .parameterValues(params)
                .pageSize(50)
                .build();
    }

    /**
     * Writer: for each Statement coming out of the processor, delete any
     * existing row for the same (account, period) and insert the new one.
     * Lets us safely re-run the job for the same month.
     */
    @Bean
    public ItemWriter<Statement> statementWriter(StatementRepository repo) {
        return chunk -> {
            for (Statement s : chunk) {
                repo.findByAccountIdAndPeriodYearAndPeriodMonth(
                        s.getAccountId(), s.getPeriodYear(), s.getPeriodMonth()
                ).ifPresent(existing -> {
                    repo.delete(existing);
                    repo.flush();   // force the DELETE to hit the DB before the INSERT
                });
                repo.save(s);
            }
            log.info("Persisted {} statement rows", chunk.size());
        };
    }
}
# EXPLAIN of the quality window's query (S-35 review F6)

PR env `szczur-radar-pr-166`, 2026-11-11, after the PR-env night: `assessments` 952 rows, `posts` 952 rows. The SQL is the one `AssessmentRepository.countBySourceAndClass` produces (JPQL `… join Post p on p.id = a.postId where a.resultSource = :resultSource and a.assessedAt >= :from and a.assessedAt < :until group by p.source, a.failureClass`), run with `railway ssh -s postgres -e szczur-radar-pr-166 -- psql -f -`.

What it shows:
- **Tonight's window** (`[2026-11-05, 2026-11-12)` in Warsaw) holds 839 of 952 rows, nearly the whole table. The planner rightly chooses `Seq Scan` + `Hash Join` (1.1 ms); an index would cost more here.
- **A selective window** (the previous night's, `[2026-11-04, 2026-11-11)`: no row) uses `Index Scan using ix_assessments_assessed_at` and a `Nested Loop` with `Index Scan using posts_pkey`. That is the plan production gets, where the 7-day window is a small part of the table (~3.7k of tens of thousands of attempts).

```text
 assessments_rows 
------------------
              952
(1 row)

 posts_rows 
------------
        952
(1 row)

                                                                                               QUERY PLAN                                                                                               
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 HashAggregate  (cost=299.44..299.52 rows=8 width=33) (actual time=1.058..1.060 rows=9.00 loops=1)
   Group Key: p.source, a.failure_class
   Batches: 1  Memory Usage: 32kB
   Buffers: shared hit=253
   ->  Hash Join  (cost=97.42..293.18 rows=835 width=41) (actual time=0.350..0.970 rows=839.00 loops=1)
         Hash Cond: (a.post_id = p.id)
         Buffers: shared hit=253
         ->  Seq Scan on assessments a  (cost=0.00..193.56 rows=835 width=51) (actual time=0.009..0.542 rows=839.00 loops=1)
               Filter: ((assessed_at >= '2026-11-04 23:00:00+00'::timestamp with time zone) AND (assessed_at < '2026-11-11 23:00:00+00'::timestamp with time zone) AND (result_source = 'model'::text))
               Rows Removed by Filter: 113
               Buffers: shared hit=177
         ->  Hash  (cost=85.52..85.52 rows=952 width=22) (actual time=0.319..0.319 rows=952.00 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 59kB
               Buffers: shared hit=76
               ->  Seq Scan on posts p  (cost=0.00..85.52 rows=952 width=22) (actual time=0.004..0.243 rows=952.00 loops=1)
                     Buffers: shared hit=76
 Planning:
   Buffers: shared hit=204
 Planning Time: 0.430 ms
 Execution Time: 1.095 ms
(20 rows)

                                                                                  QUERY PLAN                                                                                  
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 GroupAggregate  (cost=16.62..16.64 rows=1 width=33) (actual time=0.013..0.013 rows=0.00 loops=1)
   Group Key: p.source, a.failure_class
   Buffers: shared hit=5
   ->  Sort  (cost=16.62..16.62 rows=1 width=41) (actual time=0.013..0.013 rows=0.00 loops=1)
         Sort Key: p.source, a.failure_class
         Sort Method: quicksort  Memory: 25kB
         Buffers: shared hit=5
         ->  Nested Loop  (cost=0.55..16.61 rows=1 width=41) (actual time=0.002..0.003 rows=0.00 loops=1)
               Buffers: shared hit=2
               ->  Index Scan using ix_assessments_assessed_at on assessments a  (cost=0.28..8.30 rows=1 width=51) (actual time=0.002..0.002 rows=0.00 loops=1)
                     Index Cond: ((assessed_at >= '2026-11-03 23:00:00+00'::timestamp with time zone) AND (assessed_at < '2026-11-10 23:00:00+00'::timestamp with time zone))
                     Filter: (result_source = 'model'::text)
                     Index Searches: 1
                     Buffers: shared hit=2
               ->  Index Scan using posts_pkey on posts p  (cost=0.28..8.29 rows=1 width=22) (never executed)
                     Index Cond: (id = a.post_id)
                     Index Searches: 0
 Planning:
   Buffers: shared hit=18
 Planning Time: 0.142 ms
 Execution Time: 0.031 ms
(21 rows)

```

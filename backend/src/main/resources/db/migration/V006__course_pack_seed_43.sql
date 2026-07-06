-- Purpose: seed Alethicode packId=43 into Pythontopia course tables.
-- Tables affected: course_pack, course_document, course_chapter, course_page,
-- course_kc, course_example, course_problem.
-- Irreversible risk: re-running this migration overwrites manual edits to pack 43 seed rows.
-- Alethicode sources:
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V22__bootstrap_language_pack_core.sql
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V23__language_pack_documents.sql
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V24__language_pack_pages.sql
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V25__language_pack_knowledge.sql
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V26__language_pack_examples.sql
-- /home/cypress/Alethicode/backend/src/main/resources/db/migration/V40__language_pack_course_enrichment.sql

INSERT INTO course_pack (id, slug, version, name, primary_language, description, status, document_count, page_count, chapter_count, kc_count, example_count, problem_count, course_objective, target_audience, total_hours) VALUES (43, '1defa338-bf8a-425e-806a-17435ebc407a', 1, 'Python语言基础', 'Python3', '', 'published', 7, 561, 7, 65, 46, 39, NULL, '非计算机专业编程初学者', NULL) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, update_time = now();
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (186, 43, '第一章：计算工具与计算思维.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第一章：计算工具与计算思维.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第一章：计算工具与计算思维.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '110ca86b4c3e9878271b5be8d3baa91b79623094278cffb7fd635faabd56c517', 84832830, 45, 'normalized', 1) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (187, 43, '第二章：Python 语言基础(2).pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第二章：Python 语言基础(2).pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第二章：Python 语言基础(2).pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '7e67d307ad915a13af427a1173865db302bd1782559365e3a17ed0a69e9ca5fb', 9451270, 73, 'normalized', 2) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (188, 43, '第三章：计算机信息表示及计算.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第三章：计算机信息表示及计算.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第三章：计算机信息表示及计算.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'e467b4693d0165fc4d75966f81ff393e5dbc2d219ae0215f294199c1f55d0f05', 4329959, 98, 'normalized', 3) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (189, 43, '第四章：流程自动化.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第四章：流程自动化.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第四章：流程自动化.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', 'c9825447a14e818e462ecab48db1107c964ce93fe6292d182ede44264eb91367', 4653950, 74, 'normalized', 4) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (190, 43, '第五章：数据结构.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第五章：数据结构.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第五章：数据结构.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '2334e16a3f9cf646dd388e3b3bca322e8566a50da34a261889db007e4e75884c', 7554913, 105, 'normalized', 5) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (191, 43, '第六章：数据存取与分析.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第六章：数据存取与分析.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第六章：数据存取与分析.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '719aca59df5504581bfd403cd2f424fae51ab538b58f1254f2fa91782f279b79', 3121820, 50, 'normalized', 6) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_document (id, pack_id, original_filename, original_path, canonical_path, preview_pdf_path, file_hash, file_size_bytes, page_count, status, sort_order) VALUES (192, 43, '第七章：归纳与抽象.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/originals/第七章：归纳与抽象.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/tasks/42/canonical/第七章：归纳与抽象.pptx', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'e79254e4d4b1ff038af31207b651e0cd4b7e26523b6089efeeab51b0cad3e639', 6075603, 116, 'normalized', 7) ON CONFLICT (id) DO UPDATE SET page_count = EXCLUDED.page_count;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (179, 43, 1, '第一章：计算工具与计算思维', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (180, 43, 2, '第二章：Python 语言基础(2)', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (181, 43, 3, '第三章：计算机信息表示及计算', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (182, 43, 4, '第四章：流程自动化', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (183, 43, 5, '第五章：数据结构', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (184, 43, 6, '第六章：数据存取与分析', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_chapter (id, pack_id, order_index, title, summary_markdown, learning_objectives) VALUES (185, 43, 7, '第七章：归纳与抽象', '', NULL) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10682, 43, 186, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10683, 43, 186, NULL, 2, 0, '课程介绍', '课程介绍

课程介绍

课程介绍', '814957710a4bcbb0a3db973dfc616cbf580d6447748db19052c16844e023cafa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '课程介绍') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10684, 43, 186, NULL, 3, 0, '课程改革', '课程改革

课程改革
《Python 语言程序设计》->《计算思维与人工智能》
1、人工智能时代的需求
推动AI赋能千行百业（新时代的Office）
2、培养计算思维
编程技术 -> 解决问题
3、实践能力的提升
实际的项目和案例学习
4、跨学科思维的培养
将所学知识运用于其他学科和实际生活中
2025/2/18
计算思维与人工智能
3

课程改革
《Python 语言程序设计》->《计算思维与人工智能》
1、人工智能时代的需求
推动AI赋能千行百业（新时代的Office）
2、培养计算思维
编程技术 -> 解决问题
3、实践能力的提升
实际的项目和案例学习
4、跨学科思维的培养
将所学知识运用于其他学科和实际生活中
2025/2/18
计算思维与人工智能
3', 'a46190ac5ec407ca2cb36953e315b8e88c67b71ea472d82818b8298cbb9e5d6b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '课程改革
《Python 语言程序设计》->《计算思维与人工智能》
1、人工智能时代的需求
推动AI赋能千行百业（新时代的Office）
2、培养计算思维
编程技术 -> 解决问题
3、实践能力的提升
实际的项目和案例学习
4、跨学科思维的培养
将所学知识运用于其他学科和实际生活中
2025/2/18
计算思维与人工智能
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10685, 43, 186, NULL, 4, 0, '书名', '书名

书名
《Python程序设计基础》(第3版）
作者
李东方
出版社
电子工业出版社


上海市高校信息技术水平考试（原上海市计算机等级考试）（二三级）参考教材
计算思维与人工智能
教科书
2025/2/18
4

书名
《Python程序设计基础》(第3版）
作者
李东方
出版社
电子工业出版社


上海市高校信息技术水平考试（原上海市计算机等级考试）（二三级）参考教材
计算思维与人工智能
教科书
2025/2/18
4', 'a6af4794b778a4862e8d05eba6cb4f13b2cca6360cd2422b123a9fb0e2f96bf5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '书名
《Python程序设计基础》(第3版）
作者
李东方
出版社
电子工业出版社


上海市高校信息技术水平考试（原上海市计算机等级考试）（二三级）参考教材
计算思维与人工智能
教科书
2025/2/18
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10686, 43, 186, NULL, 5, 0, '考试科目及对应课程编号和名称', '考试科目及对应课程编号和名称

考试科目及对应课程编号和名称
2025/2/18
计算思维与人工智能
5
上海市高等学校信息技术水平考试通知

考试科目及对应课程编号和名称
2025/2/18
计算思维与人工智能
5
上海市高等学校信息技术水平考试通知', '9ff16574efe120aaec2ededb5c2ecaaa4292df2d23da090925101b7ba3c8de84', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '考试科目及对应课程编号和名称
2025/2/18
计算思维与人工智能
5
上海市高等学校信息技术水平考试通知') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10687, 43, 186, NULL, 6, 0, '教学方式', '教学方式

教学方式
课堂教学（32课时）
图文3号机房
上机实验（32课时）
图文3号机房
计算思维与人工智能
上机时，进TEST操作系统
TEST-stu-***
2025/2/18
6

教学方式
课堂教学（32课时）
图文3号机房
上机实验（32课时）
图文3号机房
计算思维与人工智能
上机时，进TEST操作系统
TEST-stu-***
2025/2/18
6', 'e727a0b561d5b7efeee3fa2b03d6b784af944d5fe3bcfaaa34b9b52d04efe0bc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '教学方式
课堂教学（32课时）
图文3号机房
上机实验（32课时）
图文3号机房
计算思维与人工智能
上机时，进TEST操作系统
TEST-stu-***
2025/2/18
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10688, 43, 186, NULL, 7, 0, '考核及成绩评定方式', '考核及成绩评定方式

考核及成绩评定方式
平时表现+随堂测试（30%）
期末考试（70%）
闭卷，上机考试
计算思维与人工智能
2025/2/18
7

考核及成绩评定方式
平时表现+随堂测试（30%）
期末考试（70%）
闭卷，上机考试
计算思维与人工智能
2025/2/18
7', 'c45530db7d286d590fac4e66fc87dbd6ae3998658aa4b8a74751af3ddf8baf19', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '考核及成绩评定方式
平时表现+随堂测试（30%）
期末考试（70%）
闭卷，上机考试
计算思维与人工智能
2025/2/18
7') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10689, 43, 186, NULL, 8, 0, '网络资源', '网络资源

网络资源
计算思维与人工智能
2025/2/18
8
注意：要看Python3版本视频

网络资源
计算思维与人工智能
2025/2/18
8
注意：要看Python3版本视频', '10ab29006d643e556e4580605e6c61830bc5af7338e88ebbbeb8059be7f63c8d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '网络资源
计算思维与人工智能
2025/2/18
8
注意：要看Python3版本视频') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10690, 43, 186, NULL, 9, 0, '大模型', '大模型

大模型
描述清楚问题，让大模型给出答案
2025/2/18
计算工具与计算思维
9

大模型
描述清楚问题，让大模型给出答案
2025/2/18
计算工具与计算思维
9', '77d92a1bd78319bf6b6adf7b1a01a6f5c088e215daaebbc9fb34f5cf263ee2ec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '大模型
描述清楚问题，让大模型给出答案
2025/2/18
计算工具与计算思维
9') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10691, 43, 186, NULL, 10, 0, '大模型', '大模型

大模型
答疑
2025/2/18
计算工具与计算思维
10

大模型
答疑
2025/2/18
计算工具与计算思维
10', '6f3d38cf42d520b12c11cc8666ee7ea5351cf0cce2eaba8403f4e882910496bc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '大模型
答疑
2025/2/18
计算工具与计算思维
10') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10692, 43, 186, NULL, 11, 0, '第一章：计算工具与计算思维', '第一章：计算工具与计算思维

第一章：计算工具与计算思维

第一章：计算工具与计算思维', 'c54d5ffa518b14afff8dac583d7a9dd8432dcbde3b51f58f1df3e68f5e8d107f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '第一章：计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10693, 43, 186, NULL, 12, 0, '目录', '目录

目录
1.1 计算工具演变
1.2 计算思维概述
2025/2/18
计算工具与计算思维
12

目录
1.1 计算工具演变
1.2 计算思维概述
2025/2/18
计算工具与计算思维
12', 'bcd6aebe0ae613c48a452bdf0bb5688ddf0257bca5648e4376c0fe266b08dc35', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '目录
1.1 计算工具演变
1.2 计算思维概述
2025/2/18
计算工具与计算思维
12') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10694, 43, 186, 179, 13, 0, '1.1 计算工具演变', '1.1 计算工具演变

1.1 计算工具演变
2025/2/18
计算工具与计算思维
13

1.1 计算工具演变
2025/2/18
计算工具与计算思维
13', '657eff018e98819bc9d401e7020fc1d9a081679fb5e9fae2bcf766649af9f3b9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '1.1 计算工具演变
2025/2/18
计算工具与计算思维
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10695, 43, 186, 179, 14, 0, '计算工具的演变', '计算工具的演变

计算工具的演变
计算工具的产生源于对计算的需求，自古以来，人类就在不断地发明和改进计算工具，计算工具经历了从简单到复杂，从低级到高级，从手动到自动的发展过程，而且还在不断发展。
2025/2/18
计算工具与计算思维
14

计算工具的演变
计算工具的产生源于对计算的需求，自古以来，人类就在不断地发明和改进计算工具，计算工具经历了从简单到复杂，从低级到高级，从手动到自动的发展过程，而且还在不断发展。
2025/2/18
计算工具与计算思维
14', 'd56e6a523ecfa1f9f571c84b732a615f8cc77734f80013d219c74b3f631b7bae', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算工具的演变
计算工具的产生源于对计算的需求，自古以来，人类就在不断地发明和改进计算工具，计算工具经历了从简单到复杂，从低级到高级，从手动到自动的发展过程，而且还在不断发展。
2025/2/18
计算工具与计算思维
14') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10696, 43, 186, 179, 15, 0, '手动式计算工具：结绳计数', '手动式计算工具：结绳计数

手动式计算工具：结绳计数
自原始社会开始，人类就有了计数和简单算术的需求，比如记录捕获猎物的数目。于是古人采用了结绳计数，当然也有通过石子、木棍以及刻痕记录的
2025/2/18
计算工具与计算思维
15

手动式计算工具：结绳计数
自原始社会开始，人类就有了计数和简单算术的需求，比如记录捕获猎物的数目。于是古人采用了结绳计数，当然也有通过石子、木棍以及刻痕记录的
2025/2/18
计算工具与计算思维
15', 'fde1930b5a33daf857469cc656f81c0a6ed8e18dec03fb025339c608bfcbac39', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '手动式计算工具：结绳计数
自原始社会开始，人类就有了计数和简单算术的需求，比如记录捕获猎物的数目。于是古人采用了结绳计数，当然也有通过石子、木棍以及刻痕记录的
2025/2/18
计算工具与计算思维
15') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10697, 43, 186, 179, 16, 0, '手动式计算工具：中国算盘', '手动式计算工具：中国算盘

手动式计算工具：中国算盘
中国是发明真正实用算盘的国家（公元前2世纪）
中国的算盘（硬件）——手动的计算机
珠算口诀（软件）是使中国算盘区别于其他算盘并且被广泛使用的根本原因
2025/2/18
计算工具与计算思维
16

手动式计算工具：中国算盘
中国是发明真正实用算盘的国家（公元前2世纪）
中国的算盘（硬件）——手动的计算机
珠算口诀（软件）是使中国算盘区别于其他算盘并且被广泛使用的根本原因
2025/2/18
计算工具与计算思维
16', '00b709667bfa2ab357a971c4609d8ab8f93d31ec42ec4e43b456a8429b87aa2d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '手动式计算工具：中国算盘
中国是发明真正实用算盘的国家（公元前2世纪）
中国的算盘（硬件）——手动的计算机
珠算口诀（软件）是使中国算盘区别于其他算盘并且被广泛使用的根本原因
2025/2/18
计算工具与计算思维
16') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10698, 43, 186, 179, 17, 0, '机械式计算工具：加法器', '机械式计算工具：加法器

机械式计算工具：加法器
2025/2/18
计算工具与计算思维
17
1642年，年仅19岁的法国物理学家布莱士.帕斯卡（Blaise Pascal,1623-1662）制造出第一台机械式计算器“加法器”。这台计算机器是手摇的，也称为“手摇计算机器”，只能计算加法和减法。帕斯卡从加法器的成功中得到结论：人的某些思维过程与机械过程没有差别，因此可以设想用机械来模拟人的思维活动。
加法器

机械式计算工具：加法器
2025/2/18
计算工具与计算思维
17
1642年，年仅19岁的法国物理学家布莱士.帕斯卡（Blaise Pascal,1623-1662）制造出第一台机械式计算器“加法器”。这台计算机器是手摇的，也称为“手摇计算机器”，只能计算加法和减法。帕斯卡从加法器的成功中得到结论：人的某些思维过程与机械过程没有差别，因此可以设想用机械来模拟人的思维活动。
加法器', '05429bf8f490262918aebcab06c1855a5beaf0b9632e3ec207d38f9861647fa8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '机械式计算工具：加法器
2025/2/18
计算工具与计算思维
17
1642年，年仅19岁的法国物理学家布莱士.帕斯卡（Blaise Pascal,1623-1662）制造出第一台机械式计算器“加法器”。这台计算机器是手摇的，也称为“手摇计算机器”，只能计算加法和减法。帕斯卡从加法器的成功中得到结论：人的某些思维过程与机械过程没有差别，因此可以设想用机械来模拟人的思维活动。
加法器') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10699, 43, 186, 179, 18, 0, '机械式计算工具：乘法机', '机械式计算工具：乘法机

机械式计算工具：乘法机
2025/2/18
计算工具与计算思维
18
1673年，德国数学家戈特弗里德·威廉·莱布尼茨（Gottfried Wilhelm Leibniz，1646－1716），改进了帕斯卡的加法器，制成了能进行完整四则运算的乘法机。莱布尼兹同时还提出“把计算交给机器去完成，使优秀的人才从繁重的计算中解脱出来”。同时，莱布尼兹从中国的易经八卦中获得灵感，完成了对二进制的研究。
乘法机

机械式计算工具：乘法机
2025/2/18
计算工具与计算思维
18
1673年，德国数学家戈特弗里德·威廉·莱布尼茨（Gottfried Wilhelm Leibniz，1646－1716），改进了帕斯卡的加法器，制成了能进行完整四则运算的乘法机。莱布尼兹同时还提出“把计算交给机器去完成，使优秀的人才从繁重的计算中解脱出来”。同时，莱布尼兹从中国的易经八卦中获得灵感，完成了对二进制的研究。
乘法…', 'fa1630415ea634fb9441b70cdc2f56a1242355d959ef12efa038bc60e2cbff96', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '机械式计算工具：乘法机
2025/2/18
计算工具与计算思维
18
1673年，德国数学家戈特弗里德·威廉·莱布尼茨（Gottfried Wilhelm Leibniz，1646－1716），改进了帕斯卡的加法器，制成了能进行完整四则运算的乘法机。莱布尼兹同时还提出“把计算交给机器去完成，使优秀的人才从繁重的计算中解脱出来”。同时，莱布尼兹从中国的易经八卦中获得灵感，完成了对二进制的研究。
乘法…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10700, 43, 186, 179, 19, 0, '机械式计算工具：差分机', '机械式计算工具：差分机

机械式计算工具：差分机
2025/2/18
计算工具与计算思维
19
1819年，英国著名数学家、发明家查尔斯·巴贝奇（Charles Babbage 1791- 1871）从提花纺织机上获得灵感，设计了差分机，并于1822年制造出差分机模型。所谓“差分”，就是把函数表的复杂算式转化为差分运算，用简单的加法代替平方运算。差分机是最早采用寄存器来存储数据的计算机，体现了早期程序设计思想的萌芽。
差分机

机械式计算工具：差分机
2025/2/18
计算工具与计算思维
19
1819年，英国著名数学家、发明家查尔斯·巴贝奇（Charles Babbage 1791- 1871）从提花纺织机上获得灵感，设计了差分机，并于1822年制造出差分机模型。所谓“差分”，就是把函数表的复杂算式转化为差分运算，用简单的加法代替平方运算。差分机是最早采用寄存器来存储数据的计算机，体现了早期程序设计思想的萌芽。
差分…', '3f3e43cb8b2863d9336237854352805213d202f6c8c00289387e23e210554d47', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '机械式计算工具：差分机
2025/2/18
计算工具与计算思维
19
1819年，英国著名数学家、发明家查尔斯·巴贝奇（Charles Babbage 1791- 1871）从提花纺织机上获得灵感，设计了差分机，并于1822年制造出差分机模型。所谓“差分”，就是把函数表的复杂算式转化为差分运算，用简单的加法代替平方运算。差分机是最早采用寄存器来存储数据的计算机，体现了早期程序设计思想的萌芽。
差分…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10701, 43, 186, 179, 20, 0, '机电式计算工具', '机电式计算工具

机电式计算工具
2025/2/18
计算工具与计算思维
20
随着电力技术的发展，电动式计算机逐步取代了以人工为动力的计算机。 1886年，为了完成美国人口普查的需求，德裔美籍统计学家赫尔曼·何乐礼（Herman Hollerith，1860－1929）发明了穿孔制表机，仅用1年零7个月就完成了需人工统计要10年才能完成的人口普查，制表机的发明第一次把数据转换成二进制进行处理，这种方法一直沿用到至今。何乐礼在1896年成立了制表机器公司，后来成为IBM的前身。
何乐礼和制表机

机电式计算工具
2025/2/18
计算工具与计算思维
20
随着电力技术的发展，电动式计算机逐步取代了以人工为动力的计算机。 1886年，为了完成美国人口普查的需求，德裔美籍统计学家赫尔曼·何乐礼（Herman Hollerith，1860－1929）发明了穿孔制表机，仅用1年零7个月就完成了需人工统计要10年才能完成的人口普查，制表机的发明第一次把数据转换成二进制进行处理，这种方法一直沿用到至…', 'd5cc72cbf57aca7f93f5b05192249e00b78eb42ef442eedb56156624104607b4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '机电式计算工具
2025/2/18
计算工具与计算思维
20
随着电力技术的发展，电动式计算机逐步取代了以人工为动力的计算机。 1886年，为了完成美国人口普查的需求，德裔美籍统计学家赫尔曼·何乐礼（Herman Hollerith，1860－1929）发明了穿孔制表机，仅用1年零7个月就完成了需人工统计要10年才能完成的人口普查，制表机的发明第一次把数据转换成二进制进行处理，这种方法一直沿用到至…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10702, 43, 186, 179, 21, 0, '机电式计算工具', '机电式计算工具

机电式计算工具
1904年，英国物理学家与电气工程师弗莱明（1849—1945) 发明了第一只电子二极管，是人类历史上第一只电子器件。
1907年，美国发明家德福雷斯特(1873—1961)在真空二极管的基础上加以改良，制造出第一支电子三极管。
20世纪30年代后期，许多研究者将目光投向制造电子管计算机这一领域。
2025/2/18
计算工具与计算思维
21

机电式计算工具
1904年，英国物理学家与电气工程师弗莱明（1849—1945) 发明了第一只电子二极管，是人类历史上第一只电子器件。
1907年，美国发明家德福雷斯特(1873—1961)在真空二极管的基础上加以改良，制造出第一支电子三极管。
20世纪30年代后期，许多研究者将目光投向制造电子管计算机这一领域。
2025/2/18
计算工具与计算思维
21', 'b4ade5519c3e531a93d6de08d0308a3f26927948eee101872d1ea888362d32ee', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '机电式计算工具
1904年，英国物理学家与电气工程师弗莱明（1849—1945) 发明了第一只电子二极管，是人类历史上第一只电子器件。
1907年，美国发明家德福雷斯特(1873—1961)在真空二极管的基础上加以改良，制造出第一支电子三极管。
20世纪30年代后期，许多研究者将目光投向制造电子管计算机这一领域。
2025/2/18
计算工具与计算思维
21') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10703, 43, 186, 179, 22, 0, '第一台计算机的诞生', '第一台计算机的诞生

第一台计算机的诞生
2025/2/18
计算工具与计算思维
22
20世纪40年代，在第二次世界大战期间，美国宾夕法尼亚大学电工系由莫利奇和艾克特领导，为美国陆军军械部研制了一台用于弹道轨迹计算的“电子数值积分和计算机”（Electronic Numerical Integrator and Calculator简称ENIAC）。

第一台计算机的诞生
2025/2/18
计算工具与计算思维
22
20世纪40年代，在第二次世界大战期间，美国宾夕法尼亚大学电工系由莫利奇和艾克特领导，为美国陆军军械部研制了一台用于弹道轨迹计算的“电子数值积分和计算机”（Electronic Numerical Integrator and Calculator简称ENIAC）。', 'b519a7735087f8f72d1637392a60ce22f04d633efbf5384453de6de2f1fe01aa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '第一台计算机的诞生
2025/2/18
计算工具与计算思维
22
20世纪40年代，在第二次世界大战期间，美国宾夕法尼亚大学电工系由莫利奇和艾克特领导，为美国陆军军械部研制了一台用于弹道轨迹计算的“电子数值积分和计算机”（Electronic Numerical Integrator and Calculator简称ENIAC）。') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10704, 43, 186, 179, 23, 0, '计算机的发展历程', '计算机的发展历程

计算机的发展历程
2025/2/18
计算工具与计算思维
23

计算机的发展历程
2025/2/18
计算工具与计算思维
23', 'b34a19a209c5c8ed68168299cf3b747c60bfa4c76dc3c0108a75e7225d1c6d59', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算机的发展历程
2025/2/18
计算工具与计算思维
23') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10705, 43, 186, 179, 24, 0, '计算机的发展历程', '计算机的发展历程

计算机的发展历程
2025/2/18
计算工具与计算思维
24

计算机的发展历程
2025/2/18
计算工具与计算思维
24', 'fc53a873d43ce3eef737ead2624884376f1a5dc3ce1c5395a6eab40e5dc8029b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算机的发展历程
2025/2/18
计算工具与计算思维
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10706, 43, 186, 179, 25, 0, '计算机的发展趋势', '计算机的发展趋势

计算机的发展趋势
巨型化：速度更快、存储量更大、功能更强、可靠性更高
微型化：体积更小、功耗更低、可靠性更高、携带更方便、价格更便宜
网络化：利用通信技术，把分布在不同地点的计算机互联起来，按照网络协议相互通信，以达到所有用户都可共享资源的目的
智能化：使计算机具有模拟人的感觉和思维过程的能力
多媒体化：具有对文字、数字、图片、声音、视频等进行处理的能力
2025/2/18
计算工具与计算思维
25

计算机的发展趋势
巨型化：速度更快、存储量更大、功能更强、可靠性更高
微型化：体积更小、功耗更低、可靠性更高、携带更方便、价格更便宜
网络化：利用通信技术，把分布在不同地点的计算机互联起来，按照网络协议相互通信，以达到所有用户都可共享资源的目的
智能化：使计算机具有模拟人的感觉和思维过程的能力
多媒体化：具有对文字、数字、图片、声音、视频等进行处理的能力
2025/2/18
计算工具与计算思维
2…', '568e66b3828cf54c09eb5fe7394dcf13da8694c444c2fed79b3fdaf40660739f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算机的发展趋势
巨型化：速度更快、存储量更大、功能更强、可靠性更高
微型化：体积更小、功耗更低、可靠性更高、携带更方便、价格更便宜
网络化：利用通信技术，把分布在不同地点的计算机互联起来，按照网络协议相互通信，以达到所有用户都可共享资源的目的
智能化：使计算机具有模拟人的感觉和思维过程的能力
多媒体化：具有对文字、数字、图片、声音、视频等进行处理的能力
2025/2/18
计算工具与计算思维
2…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10707, 43, 186, 179, 26, 0, '我国计算机的发展', '我国计算机的发展

我国计算机的发展
华罗庚教授，是我国计算技术的奠基人和最主要的开拓者之一，1952年在他任所长的中国科学院数学所内建立了中国第一个电子计算机科研小组。 
1956年，周总理亲自主持制定的《十二年科学技术发展规划》中，就把计算机列为发展科学技术的重点之一，并在1957年筹建了中国第一个计算技术研究所。
2025/2/18
计算工具与计算思维
26

我国计算机的发展
华罗庚教授，是我国计算技术的奠基人和最主要的开拓者之一，1952年在他任所长的中国科学院数学所内建立了中国第一个电子计算机科研小组。 
1956年，周总理亲自主持制定的《十二年科学技术发展规划》中，就把计算机列为发展科学技术的重点之一，并在1957年筹建了中国第一个计算技术研究所。
2025/2/18
计算工具与计算思维
26', 'f17fb56274ea2d5a7f448fdf3468759ff312b56e0bdbd31e0374c74b67a42b38', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '我国计算机的发展
华罗庚教授，是我国计算技术的奠基人和最主要的开拓者之一，1952年在他任所长的中国科学院数学所内建立了中国第一个电子计算机科研小组。 
1956年，周总理亲自主持制定的《十二年科学技术发展规划》中，就把计算机列为发展科学技术的重点之一，并在1957年筹建了中国第一个计算技术研究所。
2025/2/18
计算工具与计算思维
26') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10708, 43, 186, 179, 27, 0, '我国计算机的发展概况', '我国计算机的发展概况

我国计算机的发展概况
1958年，我国第一台小型电子管通用计算机由中科院计算所研制成功。
1965年，中科院计算所研制成功第一台大型晶体管计算机，该机在两弹试验中发挥了重要作用。
1974年，清华大学等单位联合设计、研制成功采用集成电路的小型计算机，运算速度达每秒100万次。
1983年，国防科技大学研制出第一台超级计算机“银河一号”，使中国成为继美国、日本之后第三个能独立设计和研制超级计算机的国家。
2002年8月10日，我国成功制造出首枚高性能通用CPU——龙芯一号。此后龙芯二号问世，龙芯三号也正在紧张的研制中。龙芯的诞生，打破了国外的长期技术垄断，结束了中国近二十年无“芯”的历史。
27
2025/2/18
计算工具与计算思维

我国计算机的发展概况
1958年，我国第一台小型电子管通用计算机由中科院计算所研制成功。
1965年，中科院计算所研制成功第一台大型晶体管计算机，该机在两弹试验中发挥了重要作用。
1974年，清华大学等单位联合设计、研制成功采用集成电路的小型计算机，运算速度达每秒100万次。
1983年，国防科技大学研制出第一台超级计算机“银河一号”，使中国成为继美国、日本之后第三个能独立设计和研制超级计算机的国…', '567afb2577cb49be6dfa81d3ef6ad1e80c6515307493ee65365b607ede63b2d2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '我国计算机的发展概况
1958年，我国第一台小型电子管通用计算机由中科院计算所研制成功。
1965年，中科院计算所研制成功第一台大型晶体管计算机，该机在两弹试验中发挥了重要作用。
1974年，清华大学等单位联合设计、研制成功采用集成电路的小型计算机，运算速度达每秒100万次。
1983年，国防科技大学研制出第一台超级计算机“银河一号”，使中国成为继美国、日本之后第三个能独立设计和研制超级计算机的国…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10709, 43, 186, 179, 28, 0, '超级计算机的发展', '超级计算机的发展

超级计算机的发展
28
运算速度为每秒2570兆次（一兆等于一万亿）。
如果用“天河一号”计算1秒钟，相当于全国13亿人连续计算88年。
如果用“天河一号”计算1天，相当于使用一台当前主流的微型计算机连续计算160年之久。
“天河一号”超级计算机
由国防科技大学研制
2010年，荣获超算排行榜全球第一。
2025/2/18
计算工具与计算思维

超级计算机的发展
28
运算速度为每秒2570兆次（一兆等于一万亿）。
如果用“天河一号”计算1秒钟，相当于全国13亿人连续计算88年。
如果用“天河一号”计算1天，相当于使用一台当前主流的微型计算机连续计算160年之久。
“天河一号”超级计算机
由国防科技大学研制
2010年，荣获超算排行榜全球第一。
2025/2/18
计算工具与计算思维', 'ccb57ca87c544949b4a28cee7a9a7f2e05ed3bdd2090d64bf94c679cedc02346', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '超级计算机的发展
28
运算速度为每秒2570兆次（一兆等于一万亿）。
如果用“天河一号”计算1秒钟，相当于全国13亿人连续计算88年。
如果用“天河一号”计算1天，相当于使用一台当前主流的微型计算机连续计算160年之久。
“天河一号”超级计算机
由国防科技大学研制
2010年，荣获超算排行榜全球第一。
2025/2/18
计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10710, 43, 186, 179, 29, 0, '比“天河一号”更强的超级计算机', '比“天河一号”更强的超级计算机

比“天河一号”更强的超级计算机
29
2016年6月至2017年11月，全国产CPU制造的“神威•太湖之光”连续四次霸占世界超算500强第一的位置，同时段“天河二号”霸占第二的位置。
2013年至2015年， 天河二号连续六届（半年一次）位居世界超算500强第一名。
2025/2/18
计算工具与计算思维

比“天河一号”更强的超级计算机
29
2016年6月至2017年11月，全国产CPU制造的“神威•太湖之光”连续四次霸占世界超算500强第一的位置，同时段“天河二号”霸占第二的位置。
2013年至2015年， 天河二号连续六届（半年一次）位居世界超算500强第一名。
2025/2/18
计算工具与计算思维', 'd097e8f58d6e25298c852e59ec99aee6eaecf9d43732770b9c07f5c5c1622e63', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '比“天河一号”更强的超级计算机
29
2016年6月至2017年11月，全国产CPU制造的“神威•太湖之光”连续四次霸占世界超算500强第一的位置，同时段“天河二号”霸占第二的位置。
2013年至2015年， 天河二号连续六届（半年一次）位居世界超算500强第一名。
2025/2/18
计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10711, 43, 186, 179, 30, 0, '量子计算机是一类遵循量子力学规律进行高速的数学和逻辑运算、存储及处理量子信息的物理装置。', '量子计算机是一类遵循量子力学规律进行高速的数学和逻辑运算、存储及处理量子信息的物理装置。

量子计算机是一类遵循量子力学规律进行高速的数学和逻辑运算、存储及处理量子信息的物理装置。
相比传统计算机，量子计算机最大的优点在于运算上。由于采用量子来处理信息，数据越多，处理的速度越快，同时数据处理也越精准。
30
量子计算机的发展
2020年12月4日，中国科学技术大学潘建伟团队成功构建量子计算原型机“九章”
2025/2/18
计算工具与计算思维

量子计算机是一类遵循量子力学规律进行高速的数学和逻辑运算、存储及处理量子信息的物理装置。
相比传统计算机，量子计算机最大的优点在于运算上。由于采用量子来处理信息，数据越多，处理的速度越快，同时数据处理也越精准。
30
量子计算机的发展
2020年12月4日，中国科学技术大学潘建伟团队成功构建量子计算原型机“九章”
2025/2/18
计算工具与计算思维', 'd5061ee053c7d0206b8522d2130bf23401ec6c24bbdcb44bc1d2cef07a6b6579', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '量子计算机是一类遵循量子力学规律进行高速的数学和逻辑运算、存储及处理量子信息的物理装置。
相比传统计算机，量子计算机最大的优点在于运算上。由于采用量子来处理信息，数据越多，处理的速度越快，同时数据处理也越精准。
30
量子计算机的发展
2020年12月4日，中国科学技术大学潘建伟团队成功构建量子计算原型机“九章”
2025/2/18
计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10712, 43, 186, 179, 31, 0, '量子计算机的发展', '量子计算机的发展

量子计算机的发展
研究成果于2021年5月7日在线发表在《科学》杂志上
31
2025/2/18
计算工具与计算思维

量子计算机的发展
研究成果于2021年5月7日在线发表在《科学》杂志上
31
2025/2/18
计算工具与计算思维', 'fb33cb69de437f784eb514e4cbe3d0234be3ed944e26caf538e66022f4cca93c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '量子计算机的发展
研究成果于2021年5月7日在线发表在《科学》杂志上
31
2025/2/18
计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10713, 43, 186, 179, 32, 0, '2021年10月26日央视新闻：关于“祖冲之二号”', '2021年10月26日央视新闻：关于“祖冲之二号”

2021年10月26日央视新闻：关于“祖冲之二号”
32
2025/2/18
计算工具与计算思维

2021年10月26日央视新闻：关于“祖冲之二号”
32
2025/2/18
计算工具与计算思维', '7fee384177f3fced177b719b25d4793747c7535935592e6228d9092c38acd2ce', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '2021年10月26日央视新闻：关于“祖冲之二号”
32
2025/2/18
计算工具与计算思维') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10714, 43, 186, NULL, 33, 0, '1.2 计算思维概述', '1.2 计算思维概述

1.2 计算思维概述
2025/2/18
计算工具与计算思维
33

1.2 计算思维概述
2025/2/18
计算工具与计算思维
33', '19f05c1180fdd02745987cf954548998381d31880b4e14d591b0a1ff81764e68', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '1.2 计算思维概述
2025/2/18
计算工具与计算思维
33') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10715, 43, 186, 179, 34, 0, '什么是计算？', '什么是计算？

什么是计算？
计算就是把一个符号串变换成另一个符号串
计算的本质是基于规则的符号串变换，更广义的说，计算是基于规则的物理状态的变换。
举例：
1+2+3=？
将一段中文文章翻译成英文
任何给定的输入，经过处理和变换，得到期望的输出的过程都可以称为计算
2025/2/18
计算工具与计算思维
34

什么是计算？
计算就是把一个符号串变换成另一个符号串
计算的本质是基于规则的符号串变换，更广义的说，计算是基于规则的物理状态的变换。
举例：
1+2+3=？
将一段中文文章翻译成英文
任何给定的输入，经过处理和变换，得到期望的输出的过程都可以称为计算
2025/2/18
计算工具与计算思维
34', '91291957008a6d09ce1803ee8ba63a2efdf69ab7b36728189d86b3aab6e298a0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '什么是计算？
计算就是把一个符号串变换成另一个符号串
计算的本质是基于规则的符号串变换，更广义的说，计算是基于规则的物理状态的变换。
举例：
1+2+3=？
将一段中文文章翻译成英文
任何给定的输入，经过处理和变换，得到期望的输出的过程都可以称为计算
2025/2/18
计算工具与计算思维
34') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10716, 43, 186, 179, 35, 0, '计算', '计算

计算
计算是人类的基本技能，也是进行科学研究的工具。在传统自然科学和人文社会科学的研究过程中，一般采用理论研究或实验研究，而计算是进行研究时有力的辅助手段，已扩展为科学概念和认知问题、解决问题的方法。
计算已经改变了科学家们的思考方式
计算博弈理论改变着经济学家的思考方式；
计算生物学改变着生物学家的思考方式；
计算物理学改变着物理学家的思考方式；
。。。。。。
2025/2/18
计算工具与计算思维
35

计算
计算是人类的基本技能，也是进行科学研究的工具。在传统自然科学和人文社会科学的研究过程中，一般采用理论研究或实验研究，而计算是进行研究时有力的辅助手段，已扩展为科学概念和认知问题、解决问题的方法。
计算已经改变了科学家们的思考方式
计算博弈理论改变着经济学家的思考方式；
计算生物学改变着生物学家的思考方式；
计算物理学改变着物理学家的思考方式；
。。。。。。
2025/2/18
计算工具与计算…', '429ef7524bd6a715f1203c92ed05cf719668d4a9f4a14abdfc120aaa0ed4cea9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算
计算是人类的基本技能，也是进行科学研究的工具。在传统自然科学和人文社会科学的研究过程中，一般采用理论研究或实验研究，而计算是进行研究时有力的辅助手段，已扩展为科学概念和认知问题、解决问题的方法。
计算已经改变了科学家们的思考方式
计算博弈理论改变着经济学家的思考方式；
计算生物学改变着生物学家的思考方式；
计算物理学改变着物理学家的思考方式；
。。。。。。
2025/2/18
计算工具与计算…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10717, 43, 186, 179, 36, 0, '计算科学', '计算科学

计算科学
计算科学是研究计算技术的一门科学，它具有促进其他科学门类发展的重要作用。
计算科学是运用高级计算能力来理解和处理复杂问题的学科。计算科学是21世纪最重要的技术领域之一，因为它对整个社会的进步都是十分重要的。
2025/2/18
计算工具与计算思维
36

计算科学
计算科学是研究计算技术的一门科学，它具有促进其他科学门类发展的重要作用。
计算科学是运用高级计算能力来理解和处理复杂问题的学科。计算科学是21世纪最重要的技术领域之一，因为它对整个社会的进步都是十分重要的。
2025/2/18
计算工具与计算思维
36', 'a978408135e48b7cef8bd331123e444cc37af4670217a0c49f4c8d2a6c215b2d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算科学
计算科学是研究计算技术的一门科学，它具有促进其他科学门类发展的重要作用。
计算科学是运用高级计算能力来理解和处理复杂问题的学科。计算科学是21世纪最重要的技术领域之一，因为它对整个社会的进步都是十分重要的。
2025/2/18
计算工具与计算思维
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10718, 43, 186, 179, 37, 0, '计算科学的特征', '计算科学的特征

计算科学的特征
计算手段的器械化，
计算过程的形式化，
计算执行的自动化，
计算对象的泛在化。
2025/2/18
计算工具与计算思维
37

计算科学的特征
计算手段的器械化，
计算过程的形式化，
计算执行的自动化，
计算对象的泛在化。
2025/2/18
计算工具与计算思维
37', '40886a288dd60655089fc31623cc0a982ee9390cea5d7778b9f99cd71685fbfb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算科学的特征
计算手段的器械化，
计算过程的形式化，
计算执行的自动化，
计算对象的泛在化。
2025/2/18
计算工具与计算思维
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10719, 43, 186, 179, 38, 0, '计算机科学', '计算机科学

计算机科学
定义：研究计算机及其周围各种现象和规律的科学，是构造计算机器的学科，也是基于自动计算进行问题求解的学科。
研究和解决的问题：是能计算且能被有效地自动计算的问题。
终极问题：是什么可以被自动的计算。
2025/2/18
计算工具与计算思维
38

计算机科学
定义：研究计算机及其周围各种现象和规律的科学，是构造计算机器的学科，也是基于自动计算进行问题求解的学科。
研究和解决的问题：是能计算且能被有效地自动计算的问题。
终极问题：是什么可以被自动的计算。
2025/2/18
计算工具与计算思维
38', '5570c14904ca5bd558f7f946f0363ffaa6515a9512433ffe30358eb5b970a14e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算机科学
定义：研究计算机及其周围各种现象和规律的科学，是构造计算机器的学科，也是基于自动计算进行问题求解的学科。
研究和解决的问题：是能计算且能被有效地自动计算的问题。
终极问题：是什么可以被自动的计算。
2025/2/18
计算工具与计算思维
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10720, 43, 186, 179, 39, 0, '科学思维', '科学思维

科学思维
三大科学
以数学为代表的理论科学
以物理为代表的实验科学
新兴的计算机科学

三大科学思维
理论思维：假设/预设  定义/性质/定理  证明---推理和演绎方法
实验思维: 实验  观察  发现推断与总结---观察与归纳方法
计算思维: 设计构造与计算  ---设计与构造方法
2025/2/18
计算工具与计算思维
39

科学思维
三大科学
以数学为代表的理论科学
以物理为代表的实验科学
新兴的计算机科学

三大科学思维
理论思维：假设/预设  定义/性质/定理  证明---推理和演绎方法
实验思维: 实验  观察  发现推断与总结---观察与归纳方法
计算思维: 设计构造与计算  ---设计与构造方法
2025/2/18
计算工具与计算思维
39', 'e71a481aaa2181beb31047ceb0faeac82568ea1bd0c7b0db788127080cd124e2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '科学思维
三大科学
以数学为代表的理论科学
以物理为代表的实验科学
新兴的计算机科学

三大科学思维
理论思维：假设/预设  定义/性质/定理  证明---推理和演绎方法
实验思维: 实验  观察  发现推断与总结---观察与归纳方法
计算思维: 设计构造与计算  ---设计与构造方法
2025/2/18
计算工具与计算思维
39') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10721, 43, 186, 179, 40, 0, '计算思维历史追溯', '计算思维历史追溯

计算思维历史追溯
计算思维不是今天才有的，从我国古代的算筹、算盘，到近代的加法器，计算器以及现代的计算机，直至目前风靡全球的互联网和云计算，无不体现着计算思维。可以说，计算思维是一种早已存在的思维活动。只是在相当长的时期内，并没有得到系统的整理和总结，也没得到应有的重视。
2025/2/18
计算工具与计算思维
40

计算思维历史追溯
计算思维不是今天才有的，从我国古代的算筹、算盘，到近代的加法器，计算器以及现代的计算机，直至目前风靡全球的互联网和云计算，无不体现着计算思维。可以说，计算思维是一种早已存在的思维活动。只是在相当长的时期内，并没有得到系统的整理和总结，也没得到应有的重视。
2025/2/18
计算工具与计算思维
40', 'b203ed0bd33ae81bd617194f485088b110da302b547474f86e2ade46f549ba58', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维历史追溯
计算思维不是今天才有的，从我国古代的算筹、算盘，到近代的加法器，计算器以及现代的计算机，直至目前风靡全球的互联网和云计算，无不体现着计算思维。可以说，计算思维是一种早已存在的思维活动。只是在相当长的时期内，并没有得到系统的整理和总结，也没得到应有的重视。
2025/2/18
计算工具与计算思维
40') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10722, 43, 186, 179, 41, 0, '计算思维的提出', '计算思维的提出

计算思维的提出
1972年，图灵奖得主艾兹赫尔·韦伯·戴克斯特拉提出“我们所使用的工具影响着我们的思维方式和思维习惯，从而也深刻地影响着我们的思维能力”。
2006年3月，美国卡内基.梅隆大学计算机科学系主任周以真教授在美国权威期刊上发表论文，对计算思维做了明确的定义。
2010年10月，中国科学技术大学陈国良院士，在第六届大学计算机课程报告论坛中倡议将计算机思维引入到大学计算机基础教学中。
2025/2/18
计算工具与计算思维
41

计算思维的提出
1972年，图灵奖得主艾兹赫尔·韦伯·戴克斯特拉提出“我们所使用的工具影响着我们的思维方式和思维习惯，从而也深刻地影响着我们的思维能力”。
2006年3月，美国卡内基.梅隆大学计算机科学系主任周以真教授在美国权威期刊上发表论文，对计算思维做了明确的定义。
2010年10月，中国科学技术大学陈国良院士，在第六届大学计算机课程报告论坛中倡议将计算机思维引入到大学计算机基础教学中。
20…', 'fb00e187aa00e9ac00741f6686c95d663de2bce6ad1310ffe5afb34566eecad2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维的提出
1972年，图灵奖得主艾兹赫尔·韦伯·戴克斯特拉提出“我们所使用的工具影响着我们的思维方式和思维习惯，从而也深刻地影响着我们的思维能力”。
2006年3月，美国卡内基.梅隆大学计算机科学系主任周以真教授在美国权威期刊上发表论文，对计算思维做了明确的定义。
2010年10月，中国科学技术大学陈国良院士，在第六届大学计算机课程报告论坛中倡议将计算机思维引入到大学计算机基础教学中。
20…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10723, 43, 186, 179, 42, 0, '计算思维的概念性定义', '计算思维的概念性定义

计算思维的概念性定义
计算思维(Computational Thinking，CT)是运用计算机科学的基础概念进行问题求解、系统设计以及人类行为理解等涵盖计算机科学之广度的一系列思维活动。
计算思维的本质是抽象和自动化。
计算思维中的抽象完全超越了物理的时空观，更为丰富和复杂，完全用符号来表示。计算思维的抽象最终是要能够机械地一步一步自动执行。
2025/2/18
计算工具与计算思维
42

计算思维的概念性定义
计算思维(Computational Thinking，CT)是运用计算机科学的基础概念进行问题求解、系统设计以及人类行为理解等涵盖计算机科学之广度的一系列思维活动。
计算思维的本质是抽象和自动化。
计算思维中的抽象完全超越了物理的时空观，更为丰富和复杂，完全用符号来表示。计算思维的抽象最终是要能够机械地一步一步自动执行。
2025/2/18
计算工具与计算思维
42', '01e536f6bbaa982477883a01b081f74663978cc89c50555d1e3461395e5df9a1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维的概念性定义
计算思维(Computational Thinking，CT)是运用计算机科学的基础概念进行问题求解、系统设计以及人类行为理解等涵盖计算机科学之广度的一系列思维活动。
计算思维的本质是抽象和自动化。
计算思维中的抽象完全超越了物理的时空观，更为丰富和复杂，完全用符号来表示。计算思维的抽象最终是要能够机械地一步一步自动执行。
2025/2/18
计算工具与计算思维
42') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10724, 43, 186, 179, 43, 0, '计算思维的操作性定义', '计算思维的操作性定义

计算思维的操作性定义
2025/2/18
计算工具与计算思维
43
各领域、学科的问题（发现问题）
问题的抽象（表征问题）
问题求解算法选择（策略与方法）
问题求解的实现（实施方案）
问题解决
结果评价
成功
失败
具体的解决流程如下：
1、发现各领域、学科的问题并进行描述，符合逻辑地组织和分析数据。
2、通过抽象，建立数学模型。
3、用算法对求解过程进行精确描述。
4、利用某种计算机语言进行编写程序，实现可能的解决方案，从中找到问题求解的最佳方案。
5、将对解决结果进行评价，如果成功，该问题得到解决，并可以将求解过程进行推广并移植到广泛的问题中。
如果失败，则分析出错原因，根据具体的原因，返回之前的某个步骤重新处理。

计算思维的操作性定义
2025/2/18
计算工具与计算思维
43
各领域、学科的问题（发现问题）
问题的抽象（表征问题）
问题求解算法选择（策略与方法）
问题求解的实现（实施方案）
问题解决
结果评价
成功
失败
具体的解决流程如下：
1、发现各领域、学科的问题并进行描述，符合逻辑地组织和分析数据。
2、通过抽象，建立数学模型。
3、用算法对求解过程进行精确描述。
4、利用某种计算机语言进行编写…', 'e3cd6c79c647f1b7ccf18ac4e0a4a8337469b175c5372a9ede1a24bfe90e803c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维的操作性定义
2025/2/18
计算工具与计算思维
43
各领域、学科的问题（发现问题）
问题的抽象（表征问题）
问题求解算法选择（策略与方法）
问题求解的实现（实施方案）
问题解决
结果评价
成功
失败
具体的解决流程如下：
1、发现各领域、学科的问题并进行描述，符合逻辑地组织和分析数据。
2、通过抽象，建立数学模型。
3、用算法对求解过程进行精确描述。
4、利用某种计算机语言进行编写…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10725, 43, 186, 179, 44, 0, '计算思维的特征', '计算思维的特征

计算思维的特征
概念化，不是程序化
像计算机科学家那样去思维意味着远不止能为计算机编程，还要求能够在抽象的多个层次上思维。
根本的，不是刻板的技能
根本技能是每一个人为了在现代社会中发挥职能所必须掌握的。
是人的，不是计算机的思维方式
计算思维是人类求解问题的一条途径，人类聪颖且富有想象力，是人类赋予计算机激情。
2025/2/18
计算工具与计算思维
44

计算思维的特征
概念化，不是程序化
像计算机科学家那样去思维意味着远不止能为计算机编程，还要求能够在抽象的多个层次上思维。
根本的，不是刻板的技能
根本技能是每一个人为了在现代社会中发挥职能所必须掌握的。
是人的，不是计算机的思维方式
计算思维是人类求解问题的一条途径，人类聪颖且富有想象力，是人类赋予计算机激情。
2025/2/18
计算工具与计算思维
44', 'fde67e0e70ad2f65fc7fd74e2a90e95c6455206df2ef6b748ed1c28debe3617e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维的特征
概念化，不是程序化
像计算机科学家那样去思维意味着远不止能为计算机编程，还要求能够在抽象的多个层次上思维。
根本的，不是刻板的技能
根本技能是每一个人为了在现代社会中发挥职能所必须掌握的。
是人的，不是计算机的思维方式
计算思维是人类求解问题的一条途径，人类聪颖且富有想象力，是人类赋予计算机激情。
2025/2/18
计算工具与计算思维
44') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10726, 43, 186, 179, 45, 0, '计算思维的特征', '计算思维的特征

计算思维的特征
数学和工程思维的互补与融合
计算机科学在本质上源自数学思维，其形式化基础建筑于数学之上，计算机科学又从本质上源自工程思维。
是思想，不是人造物
计算思维被我们用来求解问题、管理日常生活、与他人交流和互动。而且，面向所有的人，所有地方。
2025/2/18
计算工具与计算思维
45

计算思维的特征
数学和工程思维的互补与融合
计算机科学在本质上源自数学思维，其形式化基础建筑于数学之上，计算机科学又从本质上源自工程思维。
是思想，不是人造物
计算思维被我们用来求解问题、管理日常生活、与他人交流和互动。而且，面向所有的人，所有地方。
2025/2/18
计算工具与计算思维
45', '2d9015b1dea08edf077d7c8f805b8f0eed22ad9cca812bfdd5236a2e029ffc26', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第一章：计算工具与计算思维.pdf', '计算思维的特征
数学和工程思维的互补与融合
计算机科学在本质上源自数学思维，其形式化基础建筑于数学之上，计算机科学又从本质上源自工程思维。
是思想，不是人造物
计算思维被我们用来求解问题、管理日常生活、与他人交流和互动。而且，面向所有的人，所有地方。
2025/2/18
计算工具与计算思维
45') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10727, 43, 187, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10728, 43, 187, NULL, 2, 0, '第二章：Python 语言基础', '第二章：Python 语言基础

第二章：Python 语言基础

第二章：Python 语言基础', '52d8fd0e7398677dc4a7210649e099753955116bf49c048f17339387fb038bd1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '第二章：Python 语言基础') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10729, 43, 187, NULL, 3, 0, '目录', '目录

目录
2.1、程序设计语言发展历史
2.2、Python语言概述
2.3、Python语言开发环境配置
2.4、程序的运行原理
2.5、语言基本要素
2024/9/24
Python 语言基础
3

目录
2.1、程序设计语言发展历史
2.2、Python语言概述
2.3、Python语言开发环境配置
2.4、程序的运行原理
2.5、语言基本要素
2024/9/24
Python 语言基础
3', 'c92acdb606d16dad0183e68f21f4e722644da2042c6e122478ab22846a183289', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '目录
2.1、程序设计语言发展历史
2.2、Python语言概述
2.3、Python语言开发环境配置
2.4、程序的运行原理
2.5、语言基本要素
2024/9/24
Python 语言基础
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10730, 43, 187, 180, 4, 0, '2.1、程序设计语言发展历史', '2.1、程序设计语言发展历史

2.1、程序设计语言发展历史
2024/9/24
Python 语言基础
4

2.1、程序设计语言发展历史
2024/9/24
Python 语言基础
4', 'd39e5c4971cf8059a38489783fa47fc04eea3160144f5bc893532611d1993f54', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.1、程序设计语言发展历史
2024/9/24
Python 语言基础
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10731, 43, 187, 180, 5, 0, '程序设计语言发展历史', '程序设计语言发展历史

程序设计语言发展历史
程序设计语言是计算机能够理解和识别用户操作意图的一种交互体系，它按照特定规则组织计算机指令，使计算机能够自动进行各种运算处理



程序设计语言分为3大类：
机器语言、汇编语言和高级语言
Python 语言基础
2024/9/24
5
程序设计语言可看作是人与机器交流的语言

程序设计语言发展历史
程序设计语言是计算机能够理解和识别用户操作意图的一种交互体系，它按照特定规则组织计算机指令，使计算机能够自动进行各种运算处理



程序设计语言分为3大类：
机器语言、汇编语言和高级语言
Python 语言基础
2024/9/24
5
程序设计语言可看作是人与机器交流的语言', '11ac31e96e44e589e470477aa3cf544ee3f09fff39df3795f68b2a0c57c90123', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '程序设计语言发展历史
程序设计语言是计算机能够理解和识别用户操作意图的一种交互体系，它按照特定规则组织计算机指令，使计算机能够自动进行各种运算处理



程序设计语言分为3大类：
机器语言、汇编语言和高级语言
Python 语言基础
2024/9/24
5
程序设计语言可看作是人与机器交流的语言') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10732, 43, 187, 180, 6, 0, '机器语言', '机器语言

机器语言
是一种二进制语言，它直接使用二进制代码表达指令，是计算机硬件可以直接识别和执行的程序设计语言
Python 语言基础
2024/9/24
6

机器语言
是一种二进制语言，它直接使用二进制代码表达指令，是计算机硬件可以直接识别和执行的程序设计语言
Python 语言基础
2024/9/24
6', 'b9ced7351a39bce13b8884009145438cbbaba4a436cc6a9e66c3e369e5cad2b2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '机器语言
是一种二进制语言，它直接使用二进制代码表达指令，是计算机硬件可以直接识别和执行的程序设计语言
Python 语言基础
2024/9/24
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10733, 43, 187, 180, 7, 0, '示例', '示例

示例
Z = X + Y
Python 语言基础
2024/9/24
7
指令部分的示例
0000 代表 加载（LOAD）
0001 代表 存储（STORE）
0010 代表 加法（ADD）
寄存器部分的示例
0000 代表寄存器 A
0001 代表寄存器 B
存储器部分的示例
000000000000 代表地址为 0 的存储器
000000000001 代表地址为 1 的存储器
000000010000 代表地址为 16 的存储器
100000000000 代表地址为 2^11 的存储器
完成Z=X+Y计算；x数值存储在地址为1的内存单元；y数值存储在地址为2的内存单元；z的内存单元为16
机器语言示例：

0000,0000,000000000001 代表 LOAD A, 1

0010,0000,000000000010 代表 ADD A, 2

0001,0000,000000010000 代表 STORE A,16

示例
Z = X + Y
Python 语言基础
2024/9/24
7
指令部分的示例
0000 代表 加载（LOAD）
0001 代表 存储（STORE）
0010 代表 加法（ADD）
寄存器部分的示例
0000 代表寄存器 A
0001 代表寄存器 B
存储器部分的示例
000000000000 代表地址为 0 的存储器
000000000001 代表地址为 1 的存储器
00000001…', '3621c05b6f3231f536b78174417ad1349b44d1cee868f9ec31bcaad10d665fda', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '示例
Z = X + Y
Python 语言基础
2024/9/24
7
指令部分的示例
0000 代表 加载（LOAD）
0001 代表 存储（STORE）
0010 代表 加法（ADD）
寄存器部分的示例
0000 代表寄存器 A
0001 代表寄存器 B
存储器部分的示例
000000000000 代表地址为 0 的存储器
000000000001 代表地址为 1 的存储器
00000001…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10734, 43, 187, 180, 8, 0, '汇编语言', '汇编语言

汇编语言
使用助记符与机器语言中的指令进行一一对应，在计算机发展早期帮助程序员提高编程效率




机器语言和汇编语言都直接操作计算机硬件并基于此设计，统称为低级语言
Python 语言基础
2024/9/24
8
汇编语言示例：
LOAD A, 1代表 0000,0000,000000000001
ADD A, 2代表 0010,0000,000000000010
STORE A, 16代表0001,0000,000000010000

汇编语言
使用助记符与机器语言中的指令进行一一对应，在计算机发展早期帮助程序员提高编程效率




机器语言和汇编语言都直接操作计算机硬件并基于此设计，统称为低级语言
Python 语言基础
2024/9/24
8
汇编语言示例：
LOAD A, 1代表 0000,0000,000000000001
ADD A, 2代表 0010,0000,000000000010
STORE A, 16代表00…', '145d47b0b99fe43c2e28e1aaaab74b06c4a01c1860fa32a4d623fa0d3d35b68c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '汇编语言
使用助记符与机器语言中的指令进行一一对应，在计算机发展早期帮助程序员提高编程效率




机器语言和汇编语言都直接操作计算机硬件并基于此设计，统称为低级语言
Python 语言基础
2024/9/24
8
汇编语言示例：
LOAD A, 1代表 0000,0000,000000000001
ADD A, 2代表 0010,0000,000000000010
STORE A, 16代表00…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10735, 43, 187, 180, 9, 0, '高级语言', '高级语言

高级语言
高级语言是接近自然语言的一种计算机程序设计语言，更容易地描述计算问题并利用计算机解决计算问题




相对于人类语言，程序设计语言的结构在语法上十分精密，在语义上定义准确
Python 语言基础
2024/9/24
9
高级语言示例：
Z=X+Y

高级语言
高级语言是接近自然语言的一种计算机程序设计语言，更容易地描述计算问题并利用计算机解决计算问题




相对于人类语言，程序设计语言的结构在语法上十分精密，在语义上定义准确
Python 语言基础
2024/9/24
9
高级语言示例：
Z=X+Y', '6bdbaea1116d0dfaacb1a9cc9568747ca94608fad9ddf5aead3bf24112273864', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '高级语言
高级语言是接近自然语言的一种计算机程序设计语言，更容易地描述计算问题并利用计算机解决计算问题




相对于人类语言，程序设计语言的结构在语法上十分精密，在语义上定义准确
Python 语言基础
2024/9/24
9
高级语言示例：
Z=X+Y') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10736, 43, 187, 180, 10, 0, '编译和解释', '编译和解释

编译和解释
高级语言转换成机器语言的两种方法（翻译）
编译（外语资料的翻译、美剧字幕组）
解释（实时的同声传译）
2024/9/24
Python 语言基础
10
静态语言
动态语言/脚本语言

编译和解释
高级语言转换成机器语言的两种方法（翻译）
编译（外语资料的翻译、美剧字幕组）
解释（实时的同声传译）
2024/9/24
Python 语言基础
10
静态语言
动态语言/脚本语言', 'f876047e20d2a9993f86590628c1b89f26cb60cc5efc29dfa42a37a6d6cba3f8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '编译和解释
高级语言转换成机器语言的两种方法（翻译）
编译（外语资料的翻译、美剧字幕组）
解释（实时的同声传译）
2024/9/24
Python 语言基础
10
静态语言
动态语言/脚本语言') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10737, 43, 187, 180, 11, 0, '编译和解释', '编译和解释

编译和解释
编译的好处
目标代码执行速度更快
目标代码在相同操作系统上使用灵活

解释的好处
便于维护源代码
良好的跨平台可移植性
2024/9/24
Python 语言基础
11

编译和解释
编译的好处
目标代码执行速度更快
目标代码在相同操作系统上使用灵活

解释的好处
便于维护源代码
良好的跨平台可移植性
2024/9/24
Python 语言基础
11', 'fd507c157e3cd4a58967afd65bf041ea8e12331535f34c6cba7026d9a537cb6c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '编译和解释
编译的好处
目标代码执行速度更快
目标代码在相同操作系统上使用灵活

解释的好处
便于维护源代码
良好的跨平台可移植性
2024/9/24
Python 语言基础
11') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10738, 43, 187, 180, 12, 0, '2.2、Python 语言概述', '2.2、Python 语言概述

2.2、Python 语言概述
2024/9/24
Python 语言基础
12

2.2、Python 语言概述
2024/9/24
Python 语言基础
12', '1eaf6995894c89531b41c7c7d8ce582e3511ecf3a1378d5cf628e8ea4d5e4ce6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.2、Python 语言概述
2024/9/24
Python 语言基础
12') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10739, 43, 187, 180, 13, 0, '为什么选择Python作为编程入门语言？', '为什么选择Python作为编程入门语言？

为什么选择Python作为编程入门语言？
高级编程语言上千种
2024/9/24
Python 语言基础
13

为什么选择Python作为编程入门语言？
高级编程语言上千种
2024/9/24
Python 语言基础
13', 'ee7b15d9d8574c079931282a000efe118dcd9ce0e31d9ec4c53ed349fe61487c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '为什么选择Python作为编程入门语言？
高级编程语言上千种
2024/9/24
Python 语言基础
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10740, 43, 187, 180, 14, 0, '原因一：最流行', '原因一：最流行

原因一：最流行
Python 语言基础
https://spectrum.ieee.org/computing/software/the-top-programming-languages-2019
2024/9/24
14

原因一：最流行
Python 语言基础
https://spectrum.ieee.org/computing/software/the-top-programming-languages-2019
2024/9/24
14', 'c52cc579721d4d9b83dcdeba2e988539195523411560a989e1a8c275d4c561fd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因一：最流行
Python 语言基础
https://spectrum.ieee.org/computing/software/the-top-programming-languages-2019
2024/9/24
14') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10741, 43, 187, 180, 15, 0, '原因二：语法简洁、优美', '原因二：语法简洁、优美

原因二：语法简洁、优美
“Hello World”
Python 语言基础
B
2024/9/24
15

原因二：语法简洁、优美
“Hello World”
Python 语言基础
B
2024/9/24
15', 'f075cd203bc871774b805ca33f528750ab7307416fd6ba9b385222bc456441f2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因二：语法简洁、优美
“Hello World”
Python 语言基础
B
2024/9/24
15') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10742, 43, 187, 180, 16, 0, '原因二：语法简洁、优美', '原因二：语法简洁、优美

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
B
2024/9/24
16

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
B
2024/9/24
16', '8bd08e306082bf9ee17a5133dc2a8df9175cd19c56eefbf2edfec297974f7549', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
B
2024/9/24
16') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10743, 43, 187, 180, 17, 0, '原因二：语法简洁、优美', '原因二：语法简洁、优美

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
B
2024/9/24
17

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
B
2024/9/24
17', 'fc531048959fdc979fba74293d19d8a65c055c051d029aab9a1faca2c2d9761a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
B
2024/9/24
17') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10744, 43, 187, 180, 18, 0, '原因二：语法简洁、优美', '原因二：语法简洁、优美

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
PYTHON
B
2024/9/24
18

原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
PYTHON
B
2024/9/24
18', 'e4d6bc72189e4c8a1679da11929bf70d626ce9e3730eb1c049241da9c7c493ec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因二：语法简洁、优美
“Hello World”
Python 语言基础
C
JAVA
PYTHON
B
2024/9/24
18') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10745, 43, 187, 180, 19, 0, '原因三：功能强大', '原因三：功能强大

原因三：功能强大
2024/9/24
Python 语言基础
19

原因三：功能强大
2024/9/24
Python 语言基础
19', 'dbeee12512b76caefd9e60a616a32ac7bc6266e62df239450c8273d40506d909', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '原因三：功能强大
2024/9/24
Python 语言基础
19') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10746, 43, 187, 180, 20, 0, '调用 ChatGLM-6B 模型来生成对话', '调用 ChatGLM-6B 模型来生成对话

调用 ChatGLM-6B 模型来生成对话
2024/9/24
Python 语言基础
20
https://github.com/THUDM/ChatGLM-6B

调用 ChatGLM-6B 模型来生成对话
2024/9/24
Python 语言基础
20
https://github.com/THUDM/ChatGLM-6B', 'f36fd2812e4d2734b72ee28d3424494bdb8966d75f69a23306a9fbef1cd265b4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '调用 ChatGLM-6B 模型来生成对话
2024/9/24
Python 语言基础
20
https://github.com/THUDM/ChatGLM-6B') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10747, 43, 187, 180, 21, 0, '网页版', '网页版

网页版
2024/9/24
Python 语言基础
21
https://github.com/THUDM/ChatGLM-6B/blob/main/web_demo.py

网页版
2024/9/24
Python 语言基础
21
https://github.com/THUDM/ChatGLM-6B/blob/main/web_demo.py', 'fbd01919625974ba78c3fe3c07865d4df222e40db35fea966354b72c882d54c6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '网页版
2024/9/24
Python 语言基础
21
https://github.com/THUDM/ChatGLM-6B/blob/main/web_demo.py') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10748, 43, 187, 180, 22, 0, 'Guido van Rossum', 'Guido van Rossum

Guido van Rossum

Python语言创立者（1990年）

2002年-2010年，Python  2.x

2008年-至今，Python  3.x
2024/9/24
Python 语言基础
22
Python语言概述

Guido van Rossum

Python语言创立者（1990年）

2002年-2010年，Python  2.x

2008年-至今，Python  3.x
2024/9/24
Python 语言基础
22
Python语言概述', 'b084771c811ed7a1dfd89f28729f99be66ea8cb71aea1febe15e2edaf5ecf874', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'Guido van Rossum

Python语言创立者（1990年）

2002年-2010年，Python  2.x

2008年-至今，Python  3.x
2024/9/24
Python 语言基础
22
Python语言概述') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10749, 43, 187, 180, 23, 0, 'Python 2 的落幕', 'Python 2 的落幕

Python 2 的落幕
2024/9/24
Python 语言基础
23
https://pythonclock.org/

Python 2 的落幕
2024/9/24
Python 语言基础
23
https://pythonclock.org/', 'a7bd5a3642782bc76d450e98eab362860923b962664af644667a7615aa72ef62', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'Python 2 的落幕
2024/9/24
Python 语言基础
23
https://pythonclock.org/') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10750, 43, 187, 180, 24, 0, 'Python语言特点', 'Python语言特点

Python语言特点
语法简洁，强制可读
其他高级语言的1/5~1/10
脚本语言，与操作系统平台无关
灵活通用，具有优异的扩展性
科学计算、数据处理、人工智能等等
开源，具有丰富的类库
几百个内置库和函数库
十几万个第三方库
2024/9/24
Python 语言基础
24

Python语言特点
语法简洁，强制可读
其他高级语言的1/5~1/10
脚本语言，与操作系统平台无关
灵活通用，具有优异的扩展性
科学计算、数据处理、人工智能等等
开源，具有丰富的类库
几百个内置库和函数库
十几万个第三方库
2024/9/24
Python 语言基础
24', '8e162ab9a28e2aec26d65ae5511cf75f57f7eb74e6a1075284e02b7404f32155', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'Python语言特点
语法简洁，强制可读
其他高级语言的1/5~1/10
脚本语言，与操作系统平台无关
灵活通用，具有优异的扩展性
科学计算、数据处理、人工智能等等
开源，具有丰富的类库
几百个内置库和函数库
十几万个第三方库
2024/9/24
Python 语言基础
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10751, 43, 187, 180, 25, 0, '2.3、Python语言开发环境配置', '2.3、Python语言开发环境配置

2.3、Python语言开发环境配置
2024/9/24
Python 语言基础
25

2.3、Python语言开发环境配置
2024/9/24
Python 语言基础
25', 'c9769cb03574d1e9544c6195a5520ef6f3da695611e621236476bee237815454', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.3、Python语言开发环境配置
2024/9/24
Python 语言基础
25') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10752, 43, 187, 180, 26, 0, '安装', '安装

安装
下载并安装Python基本开发和运行环境
Python主页网址：www.python.org/downloads/
根据操作系统不同选择不同版本
下载相应的Python 3.5以上系列版本程序
2024/9/24
Python 语言基础
26

安装
下载并安装Python基本开发和运行环境
Python主页网址：www.python.org/downloads/
根据操作系统不同选择不同版本
下载相应的Python 3.5以上系列版本程序
2024/9/24
Python 语言基础
26', '3cd93be2f759e3863c9af5ac71f92c7777c8ad590291de0d7f52778764352459', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '安装
下载并安装Python基本开发和运行环境
Python主页网址：www.python.org/downloads/
根据操作系统不同选择不同版本
下载相应的Python 3.5以上系列版本程序
2024/9/24
Python 语言基础
26') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10753, 43, 187, 180, 27, 0, '选择可执行的安装程序', '选择可执行的安装程序

选择可执行的安装程序
2024/9/24
Python 语言基础
27

选择可执行的安装程序
2024/9/24
Python 语言基础
27', 'd46761d454797807817da615005c56bf901607065372ce91f6a86879b34ccd04', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '选择可执行的安装程序
2024/9/24
Python 语言基础
27') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10754, 43, 187, 180, 28, 0, '安装', '安装

安装
2024/9/24
Python 语言基础
28

安装
2024/9/24
Python 语言基础
28', '18fc6e6df0aaceb809c61fd2a285a87ccd8bb192f5fb4ce3aca8b29e45386aa5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '安装
2024/9/24
Python 语言基础
28') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10755, 43, 187, 180, 29, 0, '启动Python IDLE编辑器', '启动Python IDLE编辑器

启动Python IDLE编辑器
2024/9/24
Python 语言基础
29

启动Python IDLE编辑器
2024/9/24
Python 语言基础
29', '6a6c7b65711c36a4895340af7663ccbe8ae6476866459b28a0e49eaed965b805', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '启动Python IDLE编辑器
2024/9/24
Python 语言基础
29') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10756, 43, 187, 180, 30, 0, '运行程序（1）：交互式环境', '运行程序（1）：交互式环境

运行程序（1）：交互式环境
调用IDLE启动Python交互运行环境
2024/9/24
Python 语言基础
30
“一问一答”的方式
输入一行或多行程序，回车即运行

运行程序（1）：交互式环境
调用IDLE启动Python交互运行环境
2024/9/24
Python 语言基础
30
“一问一答”的方式
输入一行或多行程序，回车即运行', '0f0d7aefc63a65b19d6ee50037ea0d67d6192dbe189c50239802053d4564ea68', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '运行程序（1）：交互式环境
调用IDLE启动Python交互运行环境
2024/9/24
Python 语言基础
30
“一问一答”的方式
输入一行或多行程序，回车即运行') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10757, 43, 187, 180, 31, 0, '运行程序（2）', '运行程序（2）

运行程序（2）
使用IDLE，编辑程序文件，运行
“File”->”New File”, 打开文件编辑窗口
  编写“***.py”程序文件
“Run”->”Run Module” 运行程序，查看结果
2024/9/24
Python 语言基础
31
本课程指定
编程方式
操作类似于Word

运行程序（2）
使用IDLE，编辑程序文件，运行
“File”->”New File”, 打开文件编辑窗口
  编写“***.py”程序文件
“Run”->”Run Module” 运行程序，查看结果
2024/9/24
Python 语言基础
31
本课程指定
编程方式
操作类似于Word', 'aae4f177e5780119355dd01191ec40cc1f4dea7a21c713b74e70b24a36ebd046', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '运行程序（2）
使用IDLE，编辑程序文件，运行
“File”->”New File”, 打开文件编辑窗口
  编写“***.py”程序文件
“Run”->”Run Module” 运行程序，查看结果
2024/9/24
Python 语言基础
31
本课程指定
编程方式
操作类似于Word') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10758, 43, 187, 180, 32, 0, '运行程序（3）：不推荐', '运行程序（3）：不推荐

运行程序（3）：不推荐
启动Windows命令行工具，在命令行中输入“python”，进入Python交互运行环境
2024/9/24
Python 语言基础
32

运行程序（3）：不推荐
启动Windows命令行工具，在命令行中输入“python”，进入Python交互运行环境
2024/9/24
Python 语言基础
32', '549405f1a66d7cdea38afc7b10f7adc175d0e7fb7932d7731c4a1c4b4fdcb25d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '运行程序（3）：不推荐
启动Windows命令行工具，在命令行中输入“python”，进入Python交互运行环境
2024/9/24
Python 语言基础
32') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10759, 43, 187, 180, 33, 0, '2.4、 程序的运行原理', '2.4、 程序的运行原理

2.4、 程序的运行原理
2024/9/24
Python 语言基础
33

2.4、 程序的运行原理
2024/9/24
Python 语言基础
33', '6168f1be88b2ac296c067872aebbbe93862a9e00dffdf144e66816d796c1d0bd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.4、 程序的运行原理
2024/9/24
Python 语言基础
33') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10760, 43, 187, 180, 34, 0, '程序的原理', '程序的原理

程序的原理
计算机是根据指令操作数据的设备
程序是能够完成特定功能的一组指令序列
2024/9/24
Python 语言基础
34
输入数据
输出数据
人
机器
处理数据
指令1
指令2
指令3
指令4
……
1、输入数据
2、处理数据
3、输出数据

程序的原理
计算机是根据指令操作数据的设备
程序是能够完成特定功能的一组指令序列
2024/9/24
Python 语言基础
34
输入数据
输出数据
人
机器
处理数据
指令1
指令2
指令3
指令4
……
1、输入数据
2、处理数据
3、输出数据', '23e034ade908bbaec10d8308b64082cdbf4b2f61a3d5885f13259feb18344a9b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '程序的原理
计算机是根据指令操作数据的设备
程序是能够完成特定功能的一组指令序列
2024/9/24
Python 语言基础
34
输入数据
输出数据
人
机器
处理数据
指令1
指令2
指令3
指令4
……
1、输入数据
2、处理数据
3、输出数据') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10761, 43, 187, 180, 35, 0, '小游戏：模拟程序', '小游戏：模拟程序

小游戏：模拟程序
2024/9/24
Python 语言基础
35
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值

小游戏：模拟程序
2024/9/24
Python 语言基础
35
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值', '5341466926753fb3e1c1904c21ce56a38caafc7887b3adf70b0477024ff79dda', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '小游戏：模拟程序
2024/9/24
Python 语言基础
35
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10762, 43, 187, 180, 36, 0, '输入数据', '输入数据

输入数据
输入（Input）是一个程序的开始
程序要处理的数据有多种来源，形成了多种输入方式
内部参数输入，初始化变量
随机数据输入，使用随机数
控制台输入
交互界面输入
文件输入, 如Excel文件，数据库
网络输入，使用特定的网络协议和接口
任意数x
2024/9/24
Python 语言基础
36

输入数据
输入（Input）是一个程序的开始
程序要处理的数据有多种来源，形成了多种输入方式
内部参数输入，初始化变量
随机数据输入，使用随机数
控制台输入
交互界面输入
文件输入, 如Excel文件，数据库
网络输入，使用特定的网络协议和接口
任意数x
2024/9/24
Python 语言基础
36', '2976443045183d16459f39fa63bae7a52d99053e18a9d3fe1929218147a19ae1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '输入数据
输入（Input）是一个程序的开始
程序要处理的数据有多种来源，形成了多种输入方式
内部参数输入，初始化变量
随机数据输入，使用随机数
控制台输入
交互界面输入
文件输入, 如Excel文件，数据库
网络输入，使用特定的网络协议和接口
任意数x
2024/9/24
Python 语言基础
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10763, 43, 187, 180, 37, 0, '内部参数输入', '内部参数输入

内部参数输入
2024/9/24
Python 语言基础
37

内部参数输入
2024/9/24
Python 语言基础
37', '581389436608258d3bd2c6f2d7a777cdebbff493d11490f18a6c283f855fca2a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '内部参数输入
2024/9/24
Python 语言基础
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10764, 43, 187, 180, 38, 0, '随机数据输入', '随机数据输入

随机数据输入
2024/9/24
Python 语言基础
38

随机数据输入
2024/9/24
Python 语言基础
38', '7953b8068a79c6a0b30b10902d437e3c0c4eede46e92af05511f57df4706fa39', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '随机数据输入
2024/9/24
Python 语言基础
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10765, 43, 187, 180, 39, 0, '控制台输入', '控制台输入

控制台输入
2024/9/24
Python 语言基础
39

控制台输入
2024/9/24
Python 语言基础
39', 'c2664b4628924900d84d4adb4299bd3765f09dd4086e3ef503cbcae34d3c8cb8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '控制台输入
2024/9/24
Python 语言基础
39') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10766, 43, 187, 180, 40, 0, '交互界面输入', '交互界面输入

交互界面输入
2024/9/24
Python 语言基础
40

交互界面输入
2024/9/24
Python 语言基础
40', '414d264ca88f43cf671f36416ef03c45da342135174091a6674e28d7605508b9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '交互界面输入
2024/9/24
Python 语言基础
40') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10767, 43, 187, 180, 41, 0, '输出数据', '输出数据

输出数据
输出（Output）是程序展示运算成果的方式
程序的输出方式包括：
控制台输出
图形输出
文件输出
网络输出
操作系统内部变量输出
2024/9/24
Python 语言基础
41

输出数据
输出（Output）是程序展示运算成果的方式
程序的输出方式包括：
控制台输出
图形输出
文件输出
网络输出
操作系统内部变量输出
2024/9/24
Python 语言基础
41', '70c15eb70c83cd8edeb42b244c349de14e3c505db8ffc4e5cdc91726e2aec028', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '输出数据
输出（Output）是程序展示运算成果的方式
程序的输出方式包括：
控制台输出
图形输出
文件输出
网络输出
操作系统内部变量输出
2024/9/24
Python 语言基础
41') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10768, 43, 187, 180, 42, 0, '控制台输出', '控制台输出

控制台输出
2024/9/24
Python 语言基础
42

控制台输出
2024/9/24
Python 语言基础
42', '78bef08fa79179af5c4b25d768f7118decbdb2fe545abdb768db192e476e5e12', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '控制台输出
2024/9/24
Python 语言基础
42') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10769, 43, 187, 180, 43, 0, '图形输出', '图形输出

图形输出
2024/9/24
Python 语言基础
43

图形输出
2024/9/24
Python 语言基础
43', '95a2a0e0f80e0b97cebb9200ee80e12ea578e7e4860cdb9461db80a369e30338', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '图形输出
2024/9/24
Python 语言基础
43') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10770, 43, 187, 180, 44, 0, '处理数据', '处理数据

处理数据
处理（Process）是程序对输入数据进行计算产生输出结果的过程
处理数据可以按照是执行指令的过程
2024/9/24
Python 语言基础
44

处理数据
处理（Process）是程序对输入数据进行计算产生输出结果的过程
处理数据可以按照是执行指令的过程
2024/9/24
Python 语言基础
44', '0a8db1273a606aaa3871223725c3f1b3c8d6635afa84ed3e7eea1552c187ba11', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '处理数据
处理（Process）是程序对输入数据进行计算产生输出结果的过程
处理数据可以按照是执行指令的过程
2024/9/24
Python 语言基础
44') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10771, 43, 187, 180, 45, 0, '圆面积计算', '圆面积计算

圆面积计算
输入：圆半径radius
处理：计算圆面积area = π * radius * radius （此处，π取3.1415）
输出：圆面积area
2024/9/24
Python 语言基础
45
radius = 25 
area = 3.1415 * radius * radius
print(area)

圆面积计算
输入：圆半径radius
处理：计算圆面积area = π * radius * radius （此处，π取3.1415）
输出：圆面积area
2024/9/24
Python 语言基础
45
radius = 25 
area = 3.1415 * radius * radius
print(area)', 'e8a0a4543dd64ea5af253a70b19d3369c8cde8b7d51489cd1f9a6ae363c6b64e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '圆面积计算
输入：圆半径radius
处理：计算圆面积area = π * radius * radius （此处，π取3.1415）
输出：圆面积area
2024/9/24
Python 语言基础
45
radius = 25 
area = 3.1415 * radius * radius
print(area)') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10772, 43, 187, 180, 46, 0, '算术游戏', '算术游戏

算术游戏
2024/9/24
Python 语言基础
46
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值

算术游戏
2024/9/24
Python 语言基础
46
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值', 'cfb57039a3c3950419cec2c04ea43dbe23b34fffd00075baaf61103e04532266', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '算术游戏
2024/9/24
Python 语言基础
46
任意数x
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10773, 43, 187, NULL, 47, 0, '2.5、语言基本要素', '2.5、语言基本要素

2.5、语言基本要素
2024/9/24
Python 语言基础
47

2.5、语言基本要素
2024/9/24
Python 语言基础
47', 'd76a93367f7a2e81e373f6e3e10cb0312ce55cec9b35f125bf5684a0037c20ff', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.5、语言基本要素
2024/9/24
Python 语言基础
47') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10774, 43, 187, 180, 48, 0, '2.5.1、基本数据类型', '2.5.1、基本数据类型

2.5.1、基本数据类型
数字
字符串
2024/9/24
Python 语言基础
48

2.5.1、基本数据类型
数字
字符串
2024/9/24
Python 语言基础
48', '08b1f8fe0babd86178284ac160d4dba1e58afbd1cac80ff5aeffd09facd543c1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.5.1、基本数据类型
数字
字符串
2024/9/24
Python 语言基础
48') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10775, 43, 187, 180, 49, 0, '数字类型', '数字类型

数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2024/9/24
Python 语言基础
49

数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2024/9/24
Python 语言基础
49', '502201ca5820c80f731e8b755d540758280ccbb19ad444f387f819c3b65df9fc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2024/9/24
Python 语言基础
49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10776, 43, 187, 180, 50, 0, '字符串类型的表示', '字符串类型的表示

字符串类型的表示
字符串是字符的序列表示
有三种表示方式
单引号 ‘hello world’
双引号 “hello world”
三引号  ''''''hello world''''''
2024/9/24
Python 语言基础
50

字符串类型的表示
字符串是字符的序列表示
有三种表示方式
单引号 ‘hello world’
双引号 “hello world”
三引号  ''''''hello world''''''
2024/9/24
Python 语言基础
50', 'f1ab3829b2b65c6f174aa88716f5d042fc1045efba05e4ff8aaded6f30f96393', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '字符串类型的表示
字符串是字符的序列表示
有三种表示方式
单引号 ‘hello world’
双引号 “hello world”
三引号  ''''''hello world''''''
2024/9/24
Python 语言基础
50') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10777, 43, 187, 180, 51, 0, '不同数据类型需要严格区分', '不同数据类型需要严格区分

不同数据类型需要严格区分
例如：201015
2024/9/24
Python 语言基础
51

不同数据类型需要严格区分
例如：201015
2024/9/24
Python 语言基础
51', 'fa2d3abf4adbdeb573e33e8efeda1f5db3da927ea1335fec76ba59896b14612e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '不同数据类型需要严格区分
例如：201015
2024/9/24
Python 语言基础
51') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10778, 43, 187, 180, 52, 0, '不同数据类型需要严格区分', '不同数据类型需要严格区分

不同数据类型需要严格区分
例如：201015
数字类型：
八进制 o201015
十进制 201015
十六进制 ox201015
日期类型：2020年10月15日
字符串类型：“201015”
2024/9/24
Python 语言基础
52

不同数据类型需要严格区分
例如：201015
数字类型：
八进制 o201015
十进制 201015
十六进制 ox201015
日期类型：2020年10月15日
字符串类型：“201015”
2024/9/24
Python 语言基础
52', 'd29ce4e94de4d2c974ce54983bcc822cf663e8f751ad5ef823738731b378de52', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '不同数据类型需要严格区分
例如：201015
数字类型：
八进制 o201015
十进制 201015
十六进制 ox201015
日期类型：2020年10月15日
字符串类型：“201015”
2024/9/24
Python 语言基础
52') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10779, 43, 187, NULL, 53, 0, '一行语句代表一条指令', '一行语句代表一条指令

一行语句代表一条指令
超长语句
超过80个字符
允许但不提倡使用反斜杠连接符（“\”）
建议用圆括号来连接行
注释
程序员在代码中加入的说明信息，不被计算机执行
以“#”号开头
2024/9/24
Python 语言基础
53
2.5.2、语句

一行语句代表一条指令
超长语句
超过80个字符
允许但不提倡使用反斜杠连接符（“\”）
建议用圆括号来连接行
注释
程序员在代码中加入的说明信息，不被计算机执行
以“#”号开头
2024/9/24
Python 语言基础
53
2.5.2、语句', '2e9341c0a5a4cf7f1f760e1359153b8d873a222d1a3230c6ad9f8cfd492ebde7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '一行语句代表一条指令
超长语句
超过80个字符
允许但不提倡使用反斜杠连接符（“\”）
建议用圆括号来连接行
注释
程序员在代码中加入的说明信息，不被计算机执行
以“#”号开头
2024/9/24
Python 语言基础
53
2.5.2、语句') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10780, 43, 187, 180, 54, 0, '2.5.3、变量', '2.5.3、变量

2.5.3、变量
变量是用于存放数据的容器
通过变量名获取数据，甚至数据可以修改
2024/9/24
Python 语言基础
54
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值
a
b
c

2.5.3、变量
变量是用于存放数据的容器
通过变量名获取数据，甚至数据可以修改
2024/9/24
Python 语言基础
54
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值
a
b
c', '8818be8e81aa4a456c34a9cc4f74883eca7aa2556f92cb54e5b54c067479d392', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.5.3、变量
变量是用于存放数据的容器
通过变量名获取数据，甚至数据可以修改
2024/9/24
Python 语言基础
54
指令1：a = 2x + 1
指令2：b = 3a – 2
指令3：c = 2b + 3
指令4：说出c的值
指令5：说出a的值
a
b
c') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10781, 43, 187, 180, 55, 0, '变量的命名', '变量的命名

变量的命名
允许
采用大写字母、小写字母、数字、下划线(_)和汉字等字符及其组合给变量命名
长度没有限制
禁止
名字的首字符不能是数字
中间不能出现空格
不能和保留字名字相同
注意
标识符对大小写敏感
python和Python是两个不同的名字
2024/9/24
Python 语言基础
55

变量的命名
允许
采用大写字母、小写字母、数字、下划线(_)和汉字等字符及其组合给变量命名
长度没有限制
禁止
名字的首字符不能是数字
中间不能出现空格
不能和保留字名字相同
注意
标识符对大小写敏感
python和Python是两个不同的名字
2024/9/24
Python 语言基础
55', '0b6771aa11cc79787b0669d496c4cf2692dc7b654765ebb5bccf71393e209945', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '变量的命名
允许
采用大写字母、小写字母、数字、下划线(_)和汉字等字符及其组合给变量命名
长度没有限制
禁止
名字的首字符不能是数字
中间不能出现空格
不能和保留字名字相同
注意
标识符对大小写敏感
python和Python是两个不同的名字
2024/9/24
Python 语言基础
55') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10782, 43, 187, 180, 56, 0, '保留字', '保留字

保留字
保留字即关键字，我们不能把它们用作任何变量名称
查看关键字列表
>>> import keyword 
>>> keyword.kwlist 
[''False'', ''None'', ''True'', ''and'', ''as'', ''assert'', ''break'', ''class'', ''continue'', ''def'', ''del'', ''elif'', ''else'', ''except'', ''finally'', ''for'', ''from'', ''global'', ''if'', ''import'', ''in'', ''is'', ''lambda'', ''nonlocal'', ''not'', ''or'', ''pass'', ''raise'', ''return'', ''try'', ''while'', ''with'', ''yield'']
2024/9/24
Python 语言基础
56

保留字
保留字即关键字，我们不能把它们用作任何变量名称
查看关键字列表
>>> import keyword 
>>> keyword.kwlist 
[''False'', ''None'', ''True'', ''and'', ''as'', ''assert'', ''break'', ''class'', ''continue'', ''def'', ''del'', ''elif'', ''else'', ''except'', ''fina…', 'df1981a73947b876cc8702b181161025eeb91dd490d0291f5334cbe5c1bcd62f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '保留字
保留字即关键字，我们不能把它们用作任何变量名称
查看关键字列表
>>> import keyword 
>>> keyword.kwlist 
[''False'', ''None'', ''True'', ''and'', ''as'', ''assert'', ''break'', ''class'', ''continue'', ''def'', ''del'', ''elif'', ''else'', ''except'', ''fina…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10783, 43, 187, 180, 57, 0, '算数运算符 ', '算数运算符 

算数运算符 
+，-，*，/
% 取余
** 求幂
// 取整除
赋值运算符 
=
+=，-=，*=，/=
比较运算符 
==， !=
>, >=, <, <=
逻辑运算符 
and 与
or 或
not 非
2024/9/24
Python 语言基础
57
2.5.4、 运算符

算数运算符 
+，-，*，/
% 取余
** 求幂
// 取整除
赋值运算符 
=
+=，-=，*=，/=
比较运算符 
==， !=
>, >=, <, <=
逻辑运算符 
and 与
or 或
not 非
2024/9/24
Python 语言基础
57
2.5.4、 运算符', '867ae03124cc2574f843740f90ca4e431d5f472af260f677c7ea07ae9fdb5904', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '算数运算符 
+，-，*，/
% 取余
** 求幂
// 取整除
赋值运算符 
=
+=，-=，*=，/=
比较运算符 
==， !=
>, >=, <, <=
逻辑运算符 
and 与
or 或
not 非
2024/9/24
Python 语言基础
57
2.5.4、 运算符') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10784, 43, 187, 180, 58, 0, '赋值运算符', '赋值运算符

赋值运算符
Python语言中，= 表示“赋值”，即将等号右侧的值计算后将结果值赋给左侧变量
2024/9/24
Python 语言基础
58
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’
e = 2 * a

赋值运算符
Python语言中，= 表示“赋值”，即将等号右侧的值计算后将结果值赋给左侧变量
2024/9/24
Python 语言基础
58
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’
e = 2 * a', 'c6f024034ecc18b8312fb34f2cafef854f8dd6267ffae9edb17a0e27ccbb1ff7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '赋值运算符
Python语言中，= 表示“赋值”，即将等号右侧的值计算后将结果值赋给左侧变量
2024/9/24
Python 语言基础
58
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’
e = 2 * a') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10785, 43, 187, 180, 59, 0, '2.5.5、 函数', '2.5.5、 函数

2.5.5、 函数
抽象：将特定功能代码编写在一个函数里，便于阅读和复用，也使得程序模块化更好
函数可以理解为对一组表达特定功能表达式的封装，它与数学函数类似，能够接收变量并输出结果
内置函数
Python自带的函数，不用自己定义
2024/9/24
Python 语言基础
59
y = f(x) = 1.8x + 32

2.5.5、 函数
抽象：将特定功能代码编写在一个函数里，便于阅读和复用，也使得程序模块化更好
函数可以理解为对一组表达特定功能表达式的封装，它与数学函数类似，能够接收变量并输出结果
内置函数
Python自带的函数，不用自己定义
2024/9/24
Python 语言基础
59
y = f(x) = 1.8x + 32', '05ee86bfa2f78148bb622e334ba5fd8f09eca75d6cbf68277dcf0438b6427eed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '2.5.5、 函数
抽象：将特定功能代码编写在一个函数里，便于阅读和复用，也使得程序模块化更好
函数可以理解为对一组表达特定功能表达式的封装，它与数学函数类似，能够接收变量并输出结果
内置函数
Python自带的函数，不用自己定义
2024/9/24
Python 语言基础
59
y = f(x) = 1.8x + 32') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10786, 43, 187, 180, 60, 0, 'print()函数', 'print()函数

print()函数
控制台输出的唯一方式
输出方式一：输出变量
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
输出方式三：输出变量和字符串，通过逗号分割
print(<字符串>，<变量>，<字符串>)
2024/9/24
Python 语言基础
60
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’ 
print(a)
print(b)
print(c)
print(d)
print(“hello world”)
print(‘hello human’)
print(“hello world：”, a)
print(‘hello human：’, b)

print()函数
控制台输出的唯一方式
输出方式一：输出变量
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
输出方式三：输出变量和字符串，通过逗号分割
print(<字符串>，<变量>，<字符串>)
2024/9/24
Python 语言基础
60
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’ 
print(a)
…', '25e0895b8ee5c94d21d9233cb18b2dcb4c3deb60d68b540a7b7c6f29da852e4a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'print()函数
控制台输出的唯一方式
输出方式一：输出变量
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
输出方式三：输出变量和字符串，通过逗号分割
print(<字符串>，<变量>，<字符串>)
2024/9/24
Python 语言基础
60
a = 2
b = 3.4
c = “hello world”
d = ‘hello human’ 
print(a)
…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10787, 43, 187, 180, 61, 0, 'input()函数', 'input()函数

input()函数
控制台输入的唯一方式
<字符串变量> = input(<提示性文字>)
注意
从控制台输入获得的变量为字符串变量
2024/9/24
Python 语言基础
61

input()函数
控制台输入的唯一方式
<字符串变量> = input(<提示性文字>)
注意
从控制台输入获得的变量为字符串变量
2024/9/24
Python 语言基础
61', '63d47f0cb1ab136ef31a20c37566e293af6a0bb1e9f48ca87b0396173f86b767', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'input()函数
控制台输入的唯一方式
<字符串变量> = input(<提示性文字>)
注意
从控制台输入获得的变量为字符串变量
2024/9/24
Python 语言基础
61') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10788, 43, 187, 180, 62, 0, 'eval()函数', 'eval()函数

eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
去掉引号“”
2024/9/24
Python 语言基础
62
识别不出，则报错

eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
去掉引号“”
2024/9/24
Python 语言基础
62
识别不出，则报错', '532f290e31d07b104b011048b99f7c1cd45a893cdaa6b7f10f137a2c5e47918c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
去掉引号“”
2024/9/24
Python 语言基础
62
识别不出，则报错') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10789, 43, 187, 180, 63, 0, 'eval()函数和input()函数联用', 'eval()函数和input()函数联用

eval()函数和input()函数联用
2024/9/24
Python 语言基础
63

eval()函数和input()函数联用
2024/9/24
Python 语言基础
63', 'f9d200d1db80d43d121c76d994647aaceccac04c295e1b00015536bdd110c4be', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', 'eval()函数和input()函数联用
2024/9/24
Python 语言基础
63') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10790, 43, 187, NULL, 64, 0, '上机作业', '上机作业

上机作业
2024/9/24
Python 语言基础
64

上机作业
2024/9/24
Python 语言基础
64', '3a9c63ed961782c361625e1872395d1389aad972084634a075431893ae71027b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业
2024/9/24
Python 语言基础
64') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10791, 43, 187, NULL, 65, 0, '实验目标', '实验目标

实验目标
目标1：会安装Python语言编程开发环境
目标2：会新建、编写、保存Python程序
目标3：会使用学习通平台提交作业
2024/9/24
Python 语言基础
65
http://dhu.fanya.chaoxing.com/

实验目标
目标1：会安装Python语言编程开发环境
目标2：会新建、编写、保存Python程序
目标3：会使用学习通平台提交作业
2024/9/24
Python 语言基础
65
http://dhu.fanya.chaoxing.com/', 'c67087d7e80d47652e8f8e43fcecf5f19835f1ae46821a8f40b92e5cca4cb349', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '实验目标
目标1：会安装Python语言编程开发环境
目标2：会新建、编写、保存Python程序
目标3：会使用学习通平台提交作业
2024/9/24
Python 语言基础
65
http://dhu.fanya.chaoxing.com/') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10792, 43, 187, NULL, 66, 0, '上机作业提交说明', '上机作业提交说明

上机作业提交说明
每题包含3个文件，Python文件，代码截图和结果截图
每个python文件用学号_讲_题号命名
2024/9/24
Python 语言基础
66

上机作业提交说明
每题包含3个文件，Python文件，代码截图和结果截图
每个python文件用学号_讲_题号命名
2024/9/24
Python 语言基础
66', '560dc62f15a6c11adf7ec1dd9d245f1b0b4d7cb54cfa224c96c51ec2148e4f60', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业提交说明
每题包含3个文件，Python文件，代码截图和结果截图
每个python文件用学号_讲_题号命名
2024/9/24
Python 语言基础
66') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10793, 43, 187, NULL, 67, 0, '学习通提交作业举例', '学习通提交作业举例

学习通提交作业举例
2024/9/24
Python 语言基础
67

学习通提交作业举例
2024/9/24
Python 语言基础
67', '9e66b924947f75d1dba8b3d76ba58518016853bb59abc5e2f2dc12bced2b3ac9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '学习通提交作业举例
2024/9/24
Python 语言基础
67') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10794, 43, 187, NULL, 68, 0, '上机作业：运行Python小程序', '上机作业：运行Python小程序

上机作业：运行Python小程序
圆面积计算
简单的人名对话
斐波那契数列的计算
同切圆的绘制
日期和时间的输出
2024/9/24
Python 语言基础
68

上机作业：运行Python小程序
圆面积计算
简单的人名对话
斐波那契数列的计算
同切圆的绘制
日期和时间的输出
2024/9/24
Python 语言基础
68', 'eae97d29614e61df33864031430d1cfa1517b3385fb34c707dff95218889ea5b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业：运行Python小程序
圆面积计算
简单的人名对话
斐波那契数列的计算
同切圆的绘制
日期和时间的输出
2024/9/24
Python 语言基础
68') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10795, 43, 187, NULL, 69, 0, '上机作业1：圆面积计算', '上机作业1：圆面积计算

上机作业1：圆面积计算
2024/9/24
Python 语言基础
69

上机作业1：圆面积计算
2024/9/24
Python 语言基础
69', 'ffe1c6df89db331c9e3961082fed80dfe6cdd613775b1b0d667957be033547d4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业1：圆面积计算
2024/9/24
Python 语言基础
69') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10796, 43, 187, NULL, 70, 0, '上机作业2：简单的人名对话', '上机作业2：简单的人名对话

上机作业2：简单的人名对话
2024/9/24
Python 语言基础
70

上机作业2：简单的人名对话
2024/9/24
Python 语言基础
70', '5d71fbf0281553614d765de48f372a7472721c14efe811d2ec2ee7607a1b8e78', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业2：简单的人名对话
2024/9/24
Python 语言基础
70') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10797, 43, 187, NULL, 71, 0, '上机作业3：斐波那契数列的计算', '上机作业3：斐波那契数列的计算

上机作业3：斐波那契数列的计算
2024/9/24
Python 语言基础
71

上机作业3：斐波那契数列的计算
2024/9/24
Python 语言基础
71', 'a53059f16862ab6793488e5dd5c4078b679dae4fbfe0463a3c3f57e173946916', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业3：斐波那契数列的计算
2024/9/24
Python 语言基础
71') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10798, 43, 187, NULL, 72, 0, '上机作业4：同切圆的绘制', '上机作业4：同切圆的绘制

上机作业4：同切圆的绘制
2024/9/24
Python 语言基础
72

上机作业4：同切圆的绘制
2024/9/24
Python 语言基础
72', 'e715272395cda7ab7c3be3f59f3d4b82e6cbad1efef4da614c5a0e4311e68400', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业4：同切圆的绘制
2024/9/24
Python 语言基础
72') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10799, 43, 187, NULL, 73, 0, '上机作业5：日期和时间的输出', '上机作业5：日期和时间的输出

上机作业5：日期和时间的输出
2024/9/24
Python 语言基础
73

上机作业5：日期和时间的输出
2024/9/24
Python 语言基础
73', '3d08bc14cc438a6991b35789ac4189d86cb58150a57aef705287654538f89ab2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第二章：Python 语言基础(2).pdf', '上机作业5：日期和时间的输出
2024/9/24
Python 语言基础
73') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10800, 43, 188, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10801, 43, 188, NULL, 2, 0, '第三章：计算机信息表示及计算', '第三章：计算机信息表示及计算

第三章：计算机信息表示及计算

第三章：计算机信息表示及计算', 'a2165d084c51047e50f5a6fa77fb7d1e610fe79f1eaddfaf01e8d3687087d4e6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '第三章：计算机信息表示及计算') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10802, 43, 188, NULL, 3, 0, '目录', '目录

目录
3.1、认识“0”和“1”
3.2、“0”和“1”的思维
3.3、数值信息表示及计算
3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
3

目录
3.1、认识“0”和“1”
3.2、“0”和“1”的思维
3.3、数值信息表示及计算
3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
3', 'a27422f6f1f7ce4e014f2cbcab49241925cee4b9699e5716c38acf397d9254cd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '目录
3.1、认识“0”和“1”
3.2、“0”和“1”的思维
3.3、数值信息表示及计算
3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10803, 43, 188, NULL, 4, 0, '3.1、认识“0”和“1”', '3.1、认识“0”和“1”

3.1、认识“0”和“1”
2025/2/25
计算机信息表示及计算
4

3.1、认识“0”和“1”
2025/2/25
计算机信息表示及计算
4', '8177110ea10d2dc47b27c5082150f71c3a75c8b8c4cc877a7ba55a2731cf9f8b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '3.1、认识“0”和“1”
2025/2/25
计算机信息表示及计算
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10804, 43, 188, 181, 5, 0, '什么是进数制', '什么是进数制

什么是进数制
以表示数值所用的数字符号的个数来命名，并按一定进位规则进行计数的方法叫做进位计数制。
2025/2/25
计算机信息表示及计算
5
最常用的十进制：如重量、长度、金钱、分数、......
月份：十二进制
星期：七进制
小时：二十四进制

什么是进数制
以表示数值所用的数字符号的个数来命名，并按一定进位规则进行计数的方法叫做进位计数制。
2025/2/25
计算机信息表示及计算
5
最常用的十进制：如重量、长度、金钱、分数、......
月份：十二进制
星期：七进制
小时：二十四进制', '7b152fa4a1b2eac53c7e056228e559ed37604ec6056ea9882bb993b2991affa6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '什么是进数制
以表示数值所用的数字符号的个数来命名，并按一定进位规则进行计数的方法叫做进位计数制。
2025/2/25
计算机信息表示及计算
5
最常用的十进制：如重量、长度、金钱、分数、......
月份：十二进制
星期：七进制
小时：二十四进制') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10805, 43, 188, 181, 6, 0, '任意进制数的表示方法', '任意进制数的表示方法

任意进制数的表示方法
任何进位制都能用有限个数字符号表示所有数。
进制称为基数，如二进制的基数为2；
不同数位上的数字有不同的位权。
任意R进制数，可用公式表示：
N=An-1×Rn-1＋An-2×Rn-2＋…＋A0×R0＋A-1×R-1＋…＋A-m×R-m
A为任意进制数，R为基数，n为整数的位数和权，m为小数的位数和权
2025/2/25
计算机信息表示及计算
6

任意进制数的表示方法
任何进位制都能用有限个数字符号表示所有数。
进制称为基数，如二进制的基数为2；
不同数位上的数字有不同的位权。
任意R进制数，可用公式表示：
N=An-1×Rn-1＋An-2×Rn-2＋…＋A0×R0＋A-1×R-1＋…＋A-m×R-m
A为任意进制数，R为基数，n为整数的位数和权，m为小数的位数和权
2025/2/25
计算机信息表示及计算
6', 'b80b8bab5125611b3c93cd42c105770d23a52e1e1dcb7964aa11348bf3a15ce6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '任意进制数的表示方法
任何进位制都能用有限个数字符号表示所有数。
进制称为基数，如二进制的基数为2；
不同数位上的数字有不同的位权。
任意R进制数，可用公式表示：
N=An-1×Rn-1＋An-2×Rn-2＋…＋A0×R0＋A-1×R-1＋…＋A-m×R-m
A为任意进制数，R为基数，n为整数的位数和权，m为小数的位数和权
2025/2/25
计算机信息表示及计算
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10806, 43, 188, 181, 7, 0, '任意进制数的表示方法', '任意进制数的表示方法

任意进制数的表示方法
【例】十进制数43.21按位权展开表示。
43.21=4×101+3×100+2×10-1+1×10-2

【例】二进制数1101.11按位权展开表示。
[1101.11]2=1×23+1×22+0×21+1×2-0+1×2-1+1×2-2
2025/2/25
计算机信息表示及计算
7

任意进制数的表示方法
【例】十进制数43.21按位权展开表示。
43.21=4×101+3×100+2×10-1+1×10-2

【例】二进制数1101.11按位权展开表示。
[1101.11]2=1×23+1×22+0×21+1×2-0+1×2-1+1×2-2
2025/2/25
计算机信息表示及计算
7', '00dc75cd3b48e7c9e7f91c38c3e087463c61ab35b50e131c4e98ba4c09661dec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '任意进制数的表示方法
【例】十进制数43.21按位权展开表示。
43.21=4×101+3×100+2×10-1+1×10-2

【例】二进制数1101.11按位权展开表示。
[1101.11]2=1×23+1×22+0×21+1×2-0+1×2-1+1×2-2
2025/2/25
计算机信息表示及计算
7') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10807, 43, 188, 181, 8, 0, '二进制的产生', '二进制的产生

二进制的产生
2025/2/25
计算机信息表示及计算
8

二进制的产生
2025/2/25
计算机信息表示及计算
8', '957113ffceb63d37981cd224ce87721130e4ad94d06a5d0a7e0e1bc53f4b18ad', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制的产生
2025/2/25
计算机信息表示及计算
8') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10808, 43, 188, 181, 9, 0, '二进制的产生', '二进制的产生

二进制的产生
2025/2/25
计算机信息表示及计算
9
1                            0                  21种
11            01         10            00         22种
26种

二进制的产生
2025/2/25
计算机信息表示及计算
9
1                            0                  21种
11            01         10            00         22种
26种', '95a6cc4a3833547ea4db2f4169e2a71ea116bb4df89e37b78cfc01138139c1a8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制的产生
2025/2/25
计算机信息表示及计算
9
1                            0                  21种
11            01         10            00         22种
26种') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10809, 43, 188, 181, 10, 0, '二进制的产生', '二进制的产生

二进制的产生
从计算学科角度讲，《易经》其实是一种人工编码系统，用组合的阴阳爻符号来表达特定的信息，其中贯穿着二进制以及编码的重要思想。
2025/2/25
计算机信息表示及计算
10

二进制的产生
从计算学科角度讲，《易经》其实是一种人工编码系统，用组合的阴阳爻符号来表达特定的信息，其中贯穿着二进制以及编码的重要思想。
2025/2/25
计算机信息表示及计算
10', '5b0383dd156464316475fdbbac1c346d23f44c03b1553348e318016ddf8e72c0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制的产生
从计算学科角度讲，《易经》其实是一种人工编码系统，用组合的阴阳爻符号来表达特定的信息，其中贯穿着二进制以及编码的重要思想。
2025/2/25
计算机信息表示及计算
10') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10810, 43, 188, 181, 11, 0, '二进制的产生', '二进制的产生

二进制的产生
Gottfried Wilhelm Leibniz (1646～1716)，德国数学家
提出了二进制数及其计算规则，使用0和1来表示一切数
大胆预言计算机应该采用二进制
2025/2/25
计算机信息表示及计算
11

二进制的产生
Gottfried Wilhelm Leibniz (1646～1716)，德国数学家
提出了二进制数及其计算规则，使用0和1来表示一切数
大胆预言计算机应该采用二进制
2025/2/25
计算机信息表示及计算
11', '5beb858b749a991eb21c017d088bd6dfabce84e03f560495ba6610aba4cc72f9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制的产生
Gottfried Wilhelm Leibniz (1646～1716)，德国数学家
提出了二进制数及其计算规则，使用0和1来表示一切数
大胆预言计算机应该采用二进制
2025/2/25
计算机信息表示及计算
11') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10811, 43, 188, 181, 12, 0, '为什么计算机要使用二进制？', '为什么计算机要使用二进制？

为什么计算机要使用二进制？
二进制在物理上容易实现
电子计算机是由电子线路组成，电路通电，于是每个输出端都有了电压，可以用“1”表示高电压，用“0”表示低电压，电压的高低即转换为二进制。
二进制在物理上最容易表示和存储，要找到能存放两种状态的元件很容易，而且表面的凹凸、磁极的取向、光照的有无等都可以记录二进制。
2025/2/25
计算机信息表示及计算
12
1    0    0    1    0

为什么计算机要使用二进制？
二进制在物理上容易实现
电子计算机是由电子线路组成，电路通电，于是每个输出端都有了电压，可以用“1”表示高电压，用“0”表示低电压，电压的高低即转换为二进制。
二进制在物理上最容易表示和存储，要找到能存放两种状态的元件很容易，而且表面的凹凸、磁极的取向、光照的有无等都可以记录二进制。
2025/2/25
计算机信息表示及计算
12
1    0    0    1   …', 'dae54c943c20db42f1c8ed03bea32202b5ab64894b8e83e26a7c4765ce8610f6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么计算机要使用二进制？
二进制在物理上容易实现
电子计算机是由电子线路组成，电路通电，于是每个输出端都有了电压，可以用“1”表示高电压，用“0”表示低电压，电压的高低即转换为二进制。
二进制在物理上最容易表示和存储，要找到能存放两种状态的元件很容易，而且表面的凹凸、磁极的取向、光照的有无等都可以记录二进制。
2025/2/25
计算机信息表示及计算
12
1    0    0    1   …') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10812, 43, 188, 181, 13, 0, '为什么计算机要使用二进制？', '为什么计算机要使用二进制？

为什么计算机要使用二进制？
二进制运算规则简单
2025/2/25
计算机信息表示及计算
13
十进制加法运算规则    55条
0+0=0
1+0=1   1+1=2
2+0=2    2+1=3     2+2=4
 ...  ...
9+0=8    9+1=10   9+2=11   ... ...  9+9=18
二进制加法运算规则    4条
0+0=0      0+1=1   1+0=1   1+1=10
运算规则简单，有利于简化计算机的内部构造，提高运行效率

为什么计算机要使用二进制？
二进制运算规则简单
2025/2/25
计算机信息表示及计算
13
十进制加法运算规则    55条
0+0=0
1+0=1   1+1=2
2+0=2    2+1=3     2+2=4
 ...  ...
9+0=8    9+1=10   9+2=11   ... ...  9+9=18
二进制加法运算规则    4条
0+0=0      0+1=1   1+…', '623bf47e6f4c553e6474f33297f32290f2e34a047a416efeb893680e270dd180', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么计算机要使用二进制？
二进制运算规则简单
2025/2/25
计算机信息表示及计算
13
十进制加法运算规则    55条
0+0=0
1+0=1   1+1=2
2+0=2    2+1=3     2+2=4
 ...  ...
9+0=8    9+1=10   9+2=11   ... ...  9+9=18
二进制加法运算规则    4条
0+0=0      0+1=1   1+…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10813, 43, 188, 181, 14, 0, '为什么计算机要使用二进制？', '为什么计算机要使用二进制？

为什么计算机要使用二进制？
适合逻辑运算
逻辑运算的理论基础是逻辑代数，逻辑运算的值有两个，逻辑“真”和逻辑“假”
2025/2/25
计算机信息表示及计算
14
逻辑真——  1
逻辑假——  0

为什么计算机要使用二进制？
适合逻辑运算
逻辑运算的理论基础是逻辑代数，逻辑运算的值有两个，逻辑“真”和逻辑“假”
2025/2/25
计算机信息表示及计算
14
逻辑真——  1
逻辑假——  0', 'f634b2b88024dc7ea78e303983bafd19eacff9728beba106bde7935cfa7f46ae', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么计算机要使用二进制？
适合逻辑运算
逻辑运算的理论基础是逻辑代数，逻辑运算的值有两个，逻辑“真”和逻辑“假”
2025/2/25
计算机信息表示及计算
14
逻辑真——  1
逻辑假——  0') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10814, 43, 188, 181, 15, 0, '为什么计算机要使用二进制？', '为什么计算机要使用二进制？

为什么计算机要使用二进制？
抗干扰能力强
由于每位数据只有高和低两种状态，当受到一定程度的干扰时，仍能分辨出高低。
2025/2/25
计算机信息表示及计算
15
计算机内部采用二进制是最好的选择
进入计算机的各种数据都要进行二进制“编码”转换
从计算机输出的数据，要进行逆向的“解码”转换

为什么计算机要使用二进制？
抗干扰能力强
由于每位数据只有高和低两种状态，当受到一定程度的干扰时，仍能分辨出高低。
2025/2/25
计算机信息表示及计算
15
计算机内部采用二进制是最好的选择
进入计算机的各种数据都要进行二进制“编码”转换
从计算机输出的数据，要进行逆向的“解码”转换', '027ad3324a970b1ad3f39ab19e19ab10138a75c98b6b6af156ec2dbd3fd94318', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么计算机要使用二进制？
抗干扰能力强
由于每位数据只有高和低两种状态，当受到一定程度的干扰时，仍能分辨出高低。
2025/2/25
计算机信息表示及计算
15
计算机内部采用二进制是最好的选择
进入计算机的各种数据都要进行二进制“编码”转换
从计算机输出的数据，要进行逆向的“解码”转换') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10815, 43, 188, 181, 16, 0, '二进制数运算规则', '二进制数运算规则

二进制数运算规则
基本符号：0、1；
运算规则：逢二进一，借一当二。
四则运算规则：
（1）加法运算：0+0=0，0+1=1，1+0=1，1+1=10（有进位）
（2）减法运算：0-0=0，1-0=1，1-1=0，0-1=1（有借位）
（3）乘法运算：0×0=0，1×0=0，0×1=0，1×1=1
（4）除法运算：0÷1=0，1÷1=1（除数不能为0）
2025/2/25
计算机信息表示及计算
16

二进制数运算规则
基本符号：0、1；
运算规则：逢二进一，借一当二。
四则运算规则：
（1）加法运算：0+0=0，0+1=1，1+0=1，1+1=10（有进位）
（2）减法运算：0-0=0，1-0=1，1-1=0，0-1=1（有借位）
（3）乘法运算：0×0=0，1×0=0，0×1=0，1×1=1
（4）除法运算：0÷1=0，1÷1=1（除数不能为0）
2025/2/25
计算机信息表示及计算
1…', '6fd9de850fbdb4f9488bc7929ace37265e47ac72bc7ac0bbbc95905f04d86be4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制数运算规则
基本符号：0、1；
运算规则：逢二进一，借一当二。
四则运算规则：
（1）加法运算：0+0=0，0+1=1，1+0=1，1+1=10（有进位）
（2）减法运算：0-0=0，1-0=1，1-1=0，0-1=1（有借位）
（3）乘法运算：0×0=0，1×0=0，0×1=0，1×1=1
（4）除法运算：0÷1=0，1÷1=1（除数不能为0）
2025/2/25
计算机信息表示及计算
1…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10816, 43, 188, 181, 17, 0, '二进制数的四则运算', '二进制数的四则运算

二进制数的四则运算
2025/2/25
计算机信息表示及计算
17

二进制数的四则运算
2025/2/25
计算机信息表示及计算
17', '73e8be90b7d0f6a4c29beb4db2c93de8f710535e014dad60bd76aca5a28a41c6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制数的四则运算
2025/2/25
计算机信息表示及计算
17') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10817, 43, 188, NULL, 18, 0, '3.2、“0”和“1”的思维', '3.2、“0”和“1”的思维

3.2、“0”和“1”的思维
2025/2/25
计算机信息表示及计算
18

3.2、“0”和“1”的思维
2025/2/25
计算机信息表示及计算
18', '2a7a117dedd959542dc35c4292b23242c84dcfce6fe065609fac726111b5d4fb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '3.2、“0”和“1”的思维
2025/2/25
计算机信息表示及计算
18') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10818, 43, 188, 181, 19, 0, '“0”和“1”的思维', '“0”和“1”的思维

“0”和“1”的思维
2025/2/25
计算机信息表示及计算
19
基本思维：语义符号化符号计算化计算0(和)1化0(和) 1自动化分层构造化构造集成化

“0”和“1”的思维
2025/2/25
计算机信息表示及计算
19
基本思维：语义符号化符号计算化计算0(和)1化0(和) 1自动化分层构造化构造集成化', '076bff3de4836225bd3ad6df7bdb0f5bc55616392e86ba89ac6658a72df08567', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '“0”和“1”的思维
2025/2/25
计算机信息表示及计算
19
基本思维：语义符号化符号计算化计算0(和)1化0(和) 1自动化分层构造化构造集成化') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10819, 43, 188, 181, 20, 0, '语义符号化', '语义符号化

语义符号化
2025/2/25
计算机信息表示及计算
20

语义符号化
2025/2/25
计算机信息表示及计算
20', '98c2e15de15b7dff12dac3c2a2fd09704e66213badd50ae2b05f5eab9c2c5389', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '语义符号化
2025/2/25
计算机信息表示及计算
20') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10820, 43, 188, 181, 21, 0, '符号计算化', '符号计算化

符号计算化
2025/2/25
计算机信息表示及计算
21
算术运算
    0+0=0    0+1=1   1+0=1   1+1=10
    0-0=0     1-0=1    1-1=0    10-1=1
    0×0=0   0×1=0   1×0=0   1×1=1 
    0÷1=0   1÷1=1
逻辑运算
“与”运算 AND：有0为0 ，全1为1
“或”运算 OR：有1为1，全0为0
“非”运算 NOT：非0则1，非1则0
“异或”运算XOR：相同为0，不同为1

符号计算化
2025/2/25
计算机信息表示及计算
21
算术运算
    0+0=0    0+1=1   1+0=1   1+1=10
    0-0=0     1-0=1    1-1=0    10-1=1
    0×0=0   0×1=0   1×0=0   1×1=1 
    0÷1=0   1÷1=1
逻辑运算
“与”运算 AND：有0为0 ，全1为1
“或”运算 OR：有1…', 'b35466db3bf3fce602d55d5d990f3aec826630cf7c2b376196dacc4f6c8e1aee', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '符号计算化
2025/2/25
计算机信息表示及计算
21
算术运算
    0+0=0    0+1=1   1+0=1   1+1=10
    0-0=0     1-0=1    1-1=0    10-1=1
    0×0=0   0×1=0   1×0=0   1×1=1 
    0÷1=0   1÷1=1
逻辑运算
“与”运算 AND：有0为0 ，全1为1
“或”运算 OR：有1…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10821, 43, 188, 181, 22, 0, '计算01化', '计算01化

计算01化
2025/2/25
计算机信息表示及计算
22

计算01化
2025/2/25
计算机信息表示及计算
22', 'c871c4fa23fd7984b4a6de80178e1294b83ddb5fae7d1bdfcb1528066ffdfe3c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '计算01化
2025/2/25
计算机信息表示及计算
22') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10822, 43, 188, 181, 23, 0, '01自动化', '01自动化

01自动化
用二极管、三极管可实现基本的集成电路: 与门、或门和非门
这些电路被封装成集成电路(芯片)，即所谓的门电路
2025/2/25
计算机信息表示及计算
23

01自动化
用二极管、三极管可实现基本的集成电路: 与门、或门和非门
这些电路被封装成集成电路(芯片)，即所谓的门电路
2025/2/25
计算机信息表示及计算
23', '835db1700ff793341dde5f54ef29ce70f6a7df3c007d22791e0c0da8720623a3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '01自动化
用二极管、三极管可实现基本的集成电路: 与门、或门和非门
这些电路被封装成集成电路(芯片)，即所谓的门电路
2025/2/25
计算机信息表示及计算
23') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10823, 43, 188, 181, 24, 0, '分层构造化', '分层构造化

分层构造化
基于门电路的复杂组合逻辑电路
例：一位加法器的示例。
2025/2/25
计算机信息表示及计算
24
1
0
1
1
0
1
0
0
1

分层构造化
基于门电路的复杂组合逻辑电路
例：一位加法器的示例。
2025/2/25
计算机信息表示及计算
24
1
0
1
1
0
1
0
0
1', 'd2b21cbe69919e00a8abc1211ae7f581b2ed0dc40bf4473fdb87903fef4c9320', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '分层构造化
基于门电路的复杂组合逻辑电路
例：一位加法器的示例。
2025/2/25
计算机信息表示及计算
24
1
0
1
1
0
1
0
0
1') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10824, 43, 188, 181, 25, 0, '分层构造化', '分层构造化

分层构造化
基于门电路的复杂组合逻辑电路
例：多位加法器的示例
2025/2/25
计算机信息表示及计算
25
用已验证正确的一位加法器，来实现更为复杂的多位加法器
用已验证正确的多位加法器，来实现更为复杂的乘法器/除法器等(略) 
分层构造：低层电路已验证正确，可被封装起来；用已封装的已验证的低层电路可构造更为复杂的高层电路；如此一层层构造。

分层构造化
基于门电路的复杂组合逻辑电路
例：多位加法器的示例
2025/2/25
计算机信息表示及计算
25
用已验证正确的一位加法器，来实现更为复杂的多位加法器
用已验证正确的多位加法器，来实现更为复杂的乘法器/除法器等(略) 
分层构造：低层电路已验证正确，可被封装起来；用已封装的已验证的低层电路可构造更为复杂的高层电路；如此一层层构造。', '5e80491bc9ab0f0f232389bc146b207af34ebf848b7a185afe6c71df9d1a4167', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '分层构造化
基于门电路的复杂组合逻辑电路
例：多位加法器的示例
2025/2/25
计算机信息表示及计算
25
用已验证正确的一位加法器，来实现更为复杂的多位加法器
用已验证正确的多位加法器，来实现更为复杂的乘法器/除法器等(略) 
分层构造：低层电路已验证正确，可被封装起来；用已封装的已验证的低层电路可构造更为复杂的高层电路；如此一层层构造。') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10825, 43, 188, 181, 26, 0, '构造集成化', '构造集成化

构造集成化
复杂部件的硬件实现(芯片、主板)
微处理器芯片: 
Intel4004在12平方毫米的芯片上集成了2250个晶体管
Pentium 4处理器内建了4200万颗晶体管，以及采用0.18微米的电路
英特尔的45纳米Core 2至尊/至强四核处理器上装载了8.2亿颗晶体管
... ...
2025/2/25
计算机信息表示及计算
26

构造集成化
复杂部件的硬件实现(芯片、主板)
微处理器芯片: 
Intel4004在12平方毫米的芯片上集成了2250个晶体管
Pentium 4处理器内建了4200万颗晶体管，以及采用0.18微米的电路
英特尔的45纳米Core 2至尊/至强四核处理器上装载了8.2亿颗晶体管
... ...
2025/2/25
计算机信息表示及计算
26', '3a31397639322e4d79358d86bb8892a832172b58cc68edaf32d425fb04b4aad5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '构造集成化
复杂部件的硬件实现(芯片、主板)
微处理器芯片: 
Intel4004在12平方毫米的芯片上集成了2250个晶体管
Pentium 4处理器内建了4200万颗晶体管，以及采用0.18微米的电路
英特尔的45纳米Core 2至尊/至强四核处理器上装载了8.2亿颗晶体管
... ...
2025/2/25
计算机信息表示及计算
26') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10826, 43, 188, NULL, 27, 0, '3.3、数值信息表示及计算', '3.3、数值信息表示及计算

3.3、数值信息表示及计算
2025/2/25
计算机信息表示及计算
27

3.3、数值信息表示及计算
2025/2/25
计算机信息表示及计算
27', 'a3370abee05e2a1fa3a6f722bbfe543e4920273faffe0153e20e4e2b5b042773', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '3.3、数值信息表示及计算
2025/2/25
计算机信息表示及计算
27') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10827, 43, 188, 181, 28, 0, '数值信息表示及计算', '数值信息表示及计算

数值信息表示及计算
数字类型
2025/2/25
计算机信息表示及计算
28

数值信息表示及计算
数字类型
2025/2/25
计算机信息表示及计算
28', '5edf081b3672d3b2fbb3476cab4cb57e8206faf6920c3ac3d18a07b004e6fa0d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '数值信息表示及计算
数字类型
2025/2/25
计算机信息表示及计算
28') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10828, 43, 188, 181, 29, 0, '十进制数与二进制数的转换', '十进制数与二进制数的转换

十进制数与二进制数的转换
整数部分与小数部分分开转换；
整数部分采用除2取余法；小数部分采用乘2取整法。
【例】十进制18.8125转换为二进制数。
整数除2取余，余数从低到高排列：
18÷2=9余 0 ，9÷2=4余 1 ，4÷2=2余 0 ，2÷2=1余 0 ，1小于2不再除余 1 ；
18=[10010]2；
小数乘2取整，积的整数从高到低排列：
0.8125×2= 1 .625，0.625×2= 1 .25，0.25×2= 0 .5，0.50×2= 1 .0；
0.8125=[0.1101]2
运算结果：[18.8125]10=[10010.1101]2
2025/2/25
计算机信息表示及计算
29

十进制数与二进制数的转换
整数部分与小数部分分开转换；
整数部分采用除2取余法；小数部分采用乘2取整法。
【例】十进制18.8125转换为二进制数。
整数除2取余，余数从低到高排列：
18÷2=9余 0 ，9÷2=4余 1 ，4÷2=2余 0 ，2÷2=1余 0 ，1小于2不再除余 1 ；
18=[10010]2；
小数乘2取整，积的整数从高到低排列：
0.8125×2= 1 .625，0.625…', 'dec4589ed9b532b79a0e068b5f72765a528bf1093168d8dce7724a770f0bfa33', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '十进制数与二进制数的转换
整数部分与小数部分分开转换；
整数部分采用除2取余法；小数部分采用乘2取整法。
【例】十进制18.8125转换为二进制数。
整数除2取余，余数从低到高排列：
18÷2=9余 0 ，9÷2=4余 1 ，4÷2=2余 0 ，2÷2=1余 0 ，1小于2不再除余 1 ；
18=[10010]2；
小数乘2取整，积的整数从高到低排列：
0.8125×2= 1 .625，0.625…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10829, 43, 188, 181, 30, 0, '十进制数与二进制数的转换', '十进制数与二进制数的转换

十进制数与二进制数的转换
【例】十进制18.8125转换为二进制数的竖式计算。
2025/2/25
计算机信息表示及计算
30

十进制数与二进制数的转换
【例】十进制18.8125转换为二进制数的竖式计算。
2025/2/25
计算机信息表示及计算
30', 'dc7e5a93878db539486c335646baae05d0760001916273848654552162a03a8b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '十进制数与二进制数的转换
【例】十进制18.8125转换为二进制数的竖式计算。
2025/2/25
计算机信息表示及计算
30') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10830, 43, 188, 181, 31, 0, '二进制数与十进制数的转换', '二进制数与十进制数的转换

二进制数与十进制数的转换
下表给出了2的整数次幂和十进制数值的对应关系



将二进制数各位的数乘以对应的权再累加起来。
【例】将[1101.101]2按位权展开转换成十进制数。
    [1101.101]2＝[23＋22＋20＋2－1＋2－3]10
                       ＝[8＋4＋1＋0.5＋0.125]10＝[13.625]10
2025/2/25
计算机信息表示及计算
31

二进制数与十进制数的转换
下表给出了2的整数次幂和十进制数值的对应关系



将二进制数各位的数乘以对应的权再累加起来。
【例】将[1101.101]2按位权展开转换成十进制数。
    [1101.101]2＝[23＋22＋20＋2－1＋2－3]10
                       ＝[8＋4＋1＋0.5＋0.125]10＝[13.625]10
2025/2/25
计算机信息表示及…', '1bd3dfa51ebc67b770525bebaad77401f145e520ad0af626fca466bb2e991404', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '二进制数与十进制数的转换
下表给出了2的整数次幂和十进制数值的对应关系



将二进制数各位的数乘以对应的权再累加起来。
【例】将[1101.101]2按位权展开转换成十进制数。
    [1101.101]2＝[23＋22＋20＋2－1＋2－3]10
                       ＝[8＋4＋1＋0.5＋0.125]10＝[13.625]10
2025/2/25
计算机信息表示及…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10831, 43, 188, 181, 32, 0, '数字类型', '数字类型

数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2025/2/25
计算机信息表示及计算
32

数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2025/2/25
计算机信息表示及计算
32', 'db3d6c0e98c28447194482f9675a2b9ab43ea04391bd78ea775d01922d8534df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '数字类型
Python语言包括四种数字类型
整数 int
1
浮点数（小数）float
1.1
复数 complex
1 + 2j
布尔值 bool
True, False
2025/2/25
计算机信息表示及计算
32') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10832, 43, 188, 181, 33, 0, '内置的数值运算操作符', '内置的数值运算操作符

内置的数值运算操作符
2025/2/25
计算机信息表示及计算
33

内置的数值运算操作符
2025/2/25
计算机信息表示及计算
33', 'daaef2e12eb53646d76e14786f6951a328b053e7fcf035a9566e328c643b5010', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '内置的数值运算操作符
2025/2/25
计算机信息表示及计算
33') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10833, 43, 188, 181, 34, 0, '内置的数值运算函数', '内置的数值运算函数

内置的数值运算函数
Python解释器提供了一些内置函数，在这些内置函数之中，有6个函数与数值运算相关
2025/2/25
计算机信息表示及计算
34

内置的数值运算函数
Python解释器提供了一些内置函数，在这些内置函数之中，有6个函数与数值运算相关
2025/2/25
计算机信息表示及计算
34', 'e42d2e54776781b82d11f1e7353f866421976effa05546d9fec30707d1f95f36', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '内置的数值运算函数
Python解释器提供了一些内置函数，在这些内置函数之中，有6个函数与数值运算相关
2025/2/25
计算机信息表示及计算
34') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10834, 43, 188, 181, 35, 0, '数字类型的转换', '数字类型的转换

数字类型的转换
2025/2/25
计算机信息表示及计算
35

数字类型的转换
2025/2/25
计算机信息表示及计算
35', '025eae80246b45c54d65344d9991b1abf8a83d3dba134f3c785a4e7c06ab99a1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '数字类型的转换
2025/2/25
计算机信息表示及计算
35') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10835, 43, 188, 181, 36, 0, '数字类型的转换', '数字类型的转换

数字类型的转换
三种类型可以相互转换
函数：int(), float(), complex()
示例：
 int(4.5) = 4 （直接去掉小数部分）
 float(4) = 4.0 （增加小数部分）
 complex(4) = 4 + 0J
2025/2/25
计算机信息表示及计算
36

数字类型的转换
三种类型可以相互转换
函数：int(), float(), complex()
示例：
 int(4.5) = 4 （直接去掉小数部分）
 float(4) = 4.0 （增加小数部分）
 complex(4) = 4 + 0J
2025/2/25
计算机信息表示及计算
36', '6272e279badd3705febcf161f28e03f2b1adc1ce8f05db0bf38d53245fa4be36', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '数字类型的转换
三种类型可以相互转换
函数：int(), float(), complex()
示例：
 int(4.5) = 4 （直接去掉小数部分）
 float(4) = 4.0 （增加小数部分）
 complex(4) = 4 + 0J
2025/2/25
计算机信息表示及计算
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10836, 43, 188, 181, 37, 0, '举例：个十百千获取', '举例：个十百千获取

举例：个十百千获取
问题描述
输入一个四位数，输出它的千位数，百位数，十位数和个位数分别是多少？

例如
输入：1314
输出：千位数是1，百位数是3，十位数是1，个位数是4
2025/2/25
计算机信息表示及计算
37

举例：个十百千获取
问题描述
输入一个四位数，输出它的千位数，百位数，十位数和个位数分别是多少？

例如
输入：1314
输出：千位数是1，百位数是3，十位数是1，个位数是4
2025/2/25
计算机信息表示及计算
37', '99c5cc881522acb124ffb12069b0fe58dede8b7b2e8676950937a9a6822f48c1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '举例：个十百千获取
问题描述
输入一个四位数，输出它的千位数，百位数，十位数和个位数分别是多少？

例如
输入：1314
输出：千位数是1，百位数是3，十位数是1，个位数是4
2025/2/25
计算机信息表示及计算
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10837, 43, 188, 181, 38, 0, '数值运算', '数值运算

数值运算
2025/2/25
计算机信息表示及计算
38

数值运算
2025/2/25
计算机信息表示及计算
38', '6ac239badd531ea85654354197a642e3fddd1ed82e89e81d9fd54da19090e06c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '数值运算
2025/2/25
计算机信息表示及计算
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10838, 43, 188, 181, 39, 0, '问题解析', '问题解析

问题解析
操作符：

x = 1314
2025/2/25
计算机信息表示及计算
39

问题解析
操作符：

x = 1314
2025/2/25
计算机信息表示及计算
39', '6f1ca494f139f07ec63b0782182ed4fe6ff6b8f730bad7e3950728b54ac397ba', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '问题解析
操作符：

x = 1314
2025/2/25
计算机信息表示及计算
39') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10839, 43, 188, 181, 40, 0, '问题解析', '问题解析

问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
1
1000
2025/2/25
计算机信息表示及计算
40

问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
1
1000
2025/2/25
计算机信息表示及计算
40', 'ae83005cb5d4b240f0fa5ad5e137dc193867ad81acfc26cf0b10ac5c36194fdd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
1
1000
2025/2/25
计算机信息表示及计算
40') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10840, 43, 188, 181, 41, 0, '问题解析', '问题解析

问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
个位数： x % 10 = 4
4
    10
2025/2/25
计算机信息表示及计算
41

问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
个位数： x % 10 = 4
4
    10
2025/2/25
计算机信息表示及计算
41', 'ba7bf25ef9c4d2e01e8822c4d76c275624ac1a15eecae88967d1430bd6161ec1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '问题解析
操作符：

x = 1314
千位数： x // 1000 = 1
个位数： x % 10 = 4
4
    10
2025/2/25
计算机信息表示及计算
41') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10841, 43, 188, 181, 42, 0, '问题解析', '问题解析

问题解析
操作符：

x = 1314
千位数： x // 1000 【= 1】
个位数： x % 10 【= 4】
2025/2/25
计算机信息表示及计算
42
掐头（%）去尾（//)法

问题解析
操作符：

x = 1314
千位数： x // 1000 【= 1】
个位数： x % 10 【= 4】
2025/2/25
计算机信息表示及计算
42
掐头（%）去尾（//)法', 'd8778baec412def191d2fdd97cabebe8eb892c569cbe64b3938f2a99da49da54', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '问题解析
操作符：

x = 1314
千位数： x // 1000 【= 1】
个位数： x % 10 【= 4】
2025/2/25
计算机信息表示及计算
42
掐头（%）去尾（//)法') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10842, 43, 188, 181, 43, 0, '操作符：', '操作符：

操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
2025/2/25
计算机信息表示及计算
43
问题解析

操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
2025/2/25
计算机信息表示及计算
43
问题解析', 'e8b6750446b51fd2442c212e6c852da0e6928a000483871bc5d864d32278c82d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
2025/2/25
计算机信息表示及计算
43
问题解析') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10843, 43, 188, 181, 44, 0, '操作符：', '操作符：

操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
x = 1314
百位数：
方法二：先掐头再去尾
x % 1000 // 100 【= 3】
x % 1000 = 【314】
314 // 100 = 【3】
2025/2/25
计算机信息表示及计算
44
问题解析

操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
x = 1314
百位数：
方法二：先掐头再去尾
x % 1000 // 100 【= 3】
x % 1000 = 【314】
314 // 100 = 【3】
2025/2/25
计算机信息表示及计算
44
问题解析', '04da163b8c08cffa5cd748b56e9fef2ee29ed075c95c3a865533e86a23fe0047', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '操作符：

x = 1314
百位数：
方法一：先去尾再掐头
 x // 100 % 10 【= 3】
x // 100 【= 13】 
13 % 10 【= 3】
x = 1314
百位数：
方法二：先掐头再去尾
x % 1000 // 100 【= 3】
x % 1000 = 【314】
314 // 100 = 【3】
2025/2/25
计算机信息表示及计算
44
问题解析') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10844, 43, 188, 181, 45, 0, '操作符：', '操作符：

操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
2025/2/25
计算机信息表示及计算
45
问题解析

操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
2025/2/25
计算机信息表示及计算
45
问题解析', '1e8d9af40d8218ffd08897fc74f08c61be1557b53abbcd12201554637e74a4bb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
2025/2/25
计算机信息表示及计算
45
问题解析') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10845, 43, 188, 181, 46, 0, '操作符：', '操作符：

操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
x = 1314
十位数：
方法二：先掐头再去尾
x % 100 // 10 【= 3】
x % 100 【= 14】
14 // 10 【= 1】
2025/2/25
计算机信息表示及计算
46
问题解析

操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
x = 1314
十位数：
方法二：先掐头再去尾
x % 100 // 10 【= 3】
x % 100 【= 14】
14 // 10 【= 1】
2025/2/25
计算机信息表示及计算
46
问题解析', 'e0842b54f08da5d6e2e5e7d8875dd46fd6d0e7117ae2ab55a07a3fd45072b1ec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '操作符：

x = 1314
十位数：
方法一：先去尾再掐头
x // 10 % 10 【= 1】
x // 10 【= 131 】
131 % 10 【= 1】
x = 1314
十位数：
方法二：先掐头再去尾
x % 100 // 10 【= 3】
x % 100 【= 14】
14 // 10 【= 1】
2025/2/25
计算机信息表示及计算
46
问题解析') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10846, 43, 188, 181, 47, 0, '运行实例', '运行实例

运行实例
2025/2/25
计算机信息表示及计算
47

运行实例
2025/2/25
计算机信息表示及计算
47', '8a0c38df5f2d9d5b3083a4a5f7520a155b43dd3c23ec72a8a48c998bc562f654', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '运行实例
2025/2/25
计算机信息表示及计算
47') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10847, 43, 188, 181, 48, 0, '引入第三方库', '引入第三方库

引入第三方库
方式一：
程序可以调用库名中的所有函数，使用库中函数的格式如下：




方式二：
2025/2/25
计算机信息表示及计算
48

引入第三方库
方式一：
程序可以调用库名中的所有函数，使用库中函数的格式如下：




方式二：
2025/2/25
计算机信息表示及计算
48', '7ce5a3e1531eb6386344069349e2e4521a72a2a678e66327923303d8628c58c3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '引入第三方库
方式一：
程序可以调用库名中的所有函数，使用库中函数的格式如下：




方式二：
2025/2/25
计算机信息表示及计算
48') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10848, 43, 188, 181, 49, 0, 'random库', 'random库

random库
2025/2/25
计算机信息表示及计算
49

random库
2025/2/25
计算机信息表示及计算
49', '084fa5b8fcef83beda21d34bcd2d914ae74b4aed28d6ccf9a551cc23c9e00753', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'random库
2025/2/25
计算机信息表示及计算
49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10849, 43, 188, 181, 50, 0, '方式一引用random库', '方式一引用random库

方式一引用random库
2025/2/25
计算机信息表示及计算
50

方式一引用random库
2025/2/25
计算机信息表示及计算
50', '80c71e6642b6b75b957c89d60a95924aef7d0290d8a84dd57594a4d619f880bd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '方式一引用random库
2025/2/25
计算机信息表示及计算
50') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10850, 43, 188, 181, 51, 0, '方式二引用random库', '方式二引用random库

方式二引用random库
2025/2/25
计算机信息表示及计算
51

方式二引用random库
2025/2/25
计算机信息表示及计算
51', 'ca2b917d8e75147f146f93ae9cd0086c67c4008a173b626215c4bf6039df3645', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '方式二引用random库
2025/2/25
计算机信息表示及计算
51') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10851, 43, 188, 181, 52, 0, '方式三引用random库', '方式三引用random库

方式三引用random库
2025/2/25
计算机信息表示及计算
52

方式三引用random库
2025/2/25
计算机信息表示及计算
52', '99570ab1fb23d4be2c88b7fb5d77280c84c24093aa4ffb0d653a50e884276d6f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '方式三引用random库
2025/2/25
计算机信息表示及计算
52') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10852, 43, 188, 181, 53, 0, '3.4、文字信息表示及计算', '3.4、文字信息表示及计算

3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
53

3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
53', 'ddcb8e6b3eea1604924bad482d2abd5e7be108b2284f0a7ddc956be6b4b47c1a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '3.4、文字信息表示及计算
2025/2/25
计算机信息表示及计算
53') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10853, 43, 188, 181, 54, 0, '文字信息表示及计算', '文字信息表示及计算

文字信息表示及计算
字符串类型
2025/2/25
计算机信息表示及计算
54

文字信息表示及计算
字符串类型
2025/2/25
计算机信息表示及计算
54', 'bc7880ec63a083a66dc4c2088133376dcf5e98c2a6b9f4b178ee9065e5f7218c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '文字信息表示及计算
字符串类型
2025/2/25
计算机信息表示及计算
54') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10854, 43, 188, 181, 55, 0, '西文字符编码', '西文字符编码

西文字符编码
西文字符采用：ASCII（American Standard Code for Information Interchange,美国标准信息交换代码）
采用7位二进制编码，最高位为0
0~127 共128（27）种不同的二进制码
2025/2/25
计算机信息表示及计算
55

西文字符编码
西文字符采用：ASCII（American Standard Code for Information Interchange,美国标准信息交换代码）
采用7位二进制编码，最高位为0
0~127 共128（27）种不同的二进制码
2025/2/25
计算机信息表示及计算
55', '6621974e12413124fbc641327905bdc1aa4feb305774e826c93e1ff15b55aa49', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '西文字符编码
西文字符采用：ASCII（American Standard Code for Information Interchange,美国标准信息交换代码）
采用7位二进制编码，最高位为0
0~127 共128（27）种不同的二进制码
2025/2/25
计算机信息表示及计算
55') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10855, 43, 188, 181, 56, 0, '西文字符编码', '西文字符编码

西文字符编码
128个编码中包括：
 26个  '' A '' ~ '' Z ''
 26个  '' a '' ~ '' z ''
 10个  '' 0 '' ~ '' 9 ''
 32个 通用运算符和标点符号等
 34个控制代码
0~32、127为非图形字符，其余96个为图形字符
2025/2/25
计算机信息表示及计算
56

西文字符编码
128个编码中包括：
 26个  '' A '' ~ '' Z ''
 26个  '' a '' ~ '' z ''
 10个  '' 0 '' ~ '' 9 ''
 32个 通用运算符和标点符号等
 34个控制代码
0~32、127为非图形字符，其余96个为图形字符
2025/2/25
计算机信息表示及计算
56', '4e91590c2bbb3030d07550a6ef835e9bd83740cd216df4b430df726eb7528a5f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '西文字符编码
128个编码中包括：
 26个  '' A '' ~ '' Z ''
 26个  '' a '' ~ '' z ''
 10个  '' 0 '' ~ '' 9 ''
 32个 通用运算符和标点符号等
 34个控制代码
0~32、127为非图形字符，其余96个为图形字符
2025/2/25
计算机信息表示及计算
56') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10856, 43, 188, 181, 57, 0, '西文字符编码', '西文字符编码

西文字符编码
2025/2/25
计算机信息表示及计算
57
排列次序：D6D5D4D3D2D1D0
‘1’的ASCII码为：0110001  
  一  个  字  节  ： 00110001
对应的十进制数：49

西文字符编码
2025/2/25
计算机信息表示及计算
57
排列次序：D6D5D4D3D2D1D0
‘1’的ASCII码为：0110001  
  一  个  字  节  ： 00110001
对应的十进制数：49', 'd82c95618b4aea7e9d1b68d778900662aa2fecc3cd45ba8a1cf813a626284f4b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '西文字符编码
2025/2/25
计算机信息表示及计算
57
排列次序：D6D5D4D3D2D1D0
‘1’的ASCII码为：0110001  
  一  个  字  节  ： 00110001
对应的十进制数：49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10857, 43, 188, 181, 58, 0, '汉字编码', '汉字编码

汉字编码
汉字编码的难点：
同音字多：需要输入方法灵活
字数多：常用的四、五千左右
字形复杂：象形文字

汉字编码：
输入码、国标码、机内码 、字形码
2025/2/25
计算机信息表示及计算
58

汉字编码
汉字编码的难点：
同音字多：需要输入方法灵活
字数多：常用的四、五千左右
字形复杂：象形文字

汉字编码：
输入码、国标码、机内码 、字形码
2025/2/25
计算机信息表示及计算
58', 'e4a5cc3360195a43c303ab15b21730465114d4cc3e6d892a1fe9750d830bb6e7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字编码
汉字编码的难点：
同音字多：需要输入方法灵活
字数多：常用的四、五千左右
字形复杂：象形文字

汉字编码：
输入码、国标码、机内码 、字形码
2025/2/25
计算机信息表示及计算
58') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10858, 43, 188, 181, 59, 0, '汉字编码：输入码', '汉字编码：输入码

汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
音码类：全拼、双拼、微软拼音、智能ABC和搜狗拼音等
形码类：五笔字型法、郑码输入法等
2025/2/25
计算机信息表示及计算
59

汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
音码类：全拼、双拼、微软拼音、智能ABC和搜狗拼音等
形码类：五笔字型法、郑码输入法等
2025/2/25
计算机信息表示及计算
59', '4876524986e0b1fe1fec43d38755ac62306d00715c0d2fa9275d8f8733b25ef9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
音码类：全拼、双拼、微软拼音、智能ABC和搜狗拼音等
形码类：五笔字型法、郑码输入法等
2025/2/25
计算机信息表示及计算
59') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10859, 43, 188, 181, 60, 0, '汉字编码：输入码', '汉字编码：输入码

汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
例：输入“型”这个汉字
拼音码： xing
五笔字型码： gajf（g表字根“-”,a表开下的草字头,j表右侧立刀,f表下面土字）
2025/2/25
计算机信息表示及计算
60

汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
例：输入“型”这个汉字
拼音码： xing
五笔字型码： gajf（g表字根“-”,a表开下的草字头,j表右侧立刀,f表下面土字）
2025/2/25
计算机信息表示及计算
60', '8fcfe0a9bfdf23b90d52be0c796283870aac8c2773bb802073d8d2bbc246c232', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字编码：输入码
汉字输入码：汉字输入码是用键盘上的字母符号对应每一汉字的编码,它使人们通过键入字母符号代替键入汉字。
例：输入“型”这个汉字
拼音码： xing
五笔字型码： gajf（g表字根“-”,a表开下的草字头,j表右侧立刀,f表下面土字）
2025/2/25
计算机信息表示及计算
60') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10860, 43, 188, 181, 61, 0, '汉字编码：国标码', '汉字编码：国标码

汉字编码：国标码
国标码（代号GB2312-80）
我国1980年发布的《信息交换用汉字编码字符集—基本集》，是中文信息处理的国家标准，简称国标码
一级汉字3755个，二级汉字3008个
2025/2/25
计算机信息表示及计算
61

汉字编码：国标码
国标码（代号GB2312-80）
我国1980年发布的《信息交换用汉字编码字符集—基本集》，是中文信息处理的国家标准，简称国标码
一级汉字3755个，二级汉字3008个
2025/2/25
计算机信息表示及计算
61', '3e27df4b97aaf30df4a9e7f7a873e1bba65674e18932db6c16856f0eb64e6dfb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字编码：国标码
国标码（代号GB2312-80）
我国1980年发布的《信息交换用汉字编码字符集—基本集》，是中文信息处理的国家标准，简称国标码
一级汉字3755个，二级汉字3008个
2025/2/25
计算机信息表示及计算
61') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10861, 43, 188, 181, 62, 0, '汉字编码：机内码', '汉字编码：机内码

汉字编码：机内码
机内码
汉字被计算机系统内部处理和存储而使用的代码（由国标码演化而来）
2025/2/25
计算机信息表示及计算
62

汉字编码：机内码
机内码
汉字被计算机系统内部处理和存储而使用的代码（由国标码演化而来）
2025/2/25
计算机信息表示及计算
62', '0c211aea25b2636a1b953e360de3c924e219194edba4fffb236baabaf149ddc7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字编码：机内码
机内码
汉字被计算机系统内部处理和存储而使用的代码（由国标码演化而来）
2025/2/25
计算机信息表示及计算
62') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10862, 43, 188, 181, 63, 0, '在显示器或打印机上输出汉字时需要字形信息。', '在显示器或打印机上输出汉字时需要字形信息。

在显示器或打印机上输出汉字时需要字形信息。
汉字字形码是用0和1编码无亮点和有亮点像素,形成字形的一种编码。
主要分两类：点阵式和矢量式
2025/2/25
计算机信息表示及计算
63
汉字编码：汉字字形码
输入码
（外码）
国标码
机内码
字形码

在显示器或打印机上输出汉字时需要字形信息。
汉字字形码是用0和1编码无亮点和有亮点像素,形成字形的一种编码。
主要分两类：点阵式和矢量式
2025/2/25
计算机信息表示及计算
63
汉字编码：汉字字形码
输入码
（外码）
国标码
机内码
字形码', '26c419b2f61660e69b481bee719f8a8227d238f9f074782cfa9989a8b8b66efe', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '在显示器或打印机上输出汉字时需要字形信息。
汉字字形码是用0和1编码无亮点和有亮点像素,形成字形的一种编码。
主要分两类：点阵式和矢量式
2025/2/25
计算机信息表示及计算
63
汉字编码：汉字字形码
输入码
（外码）
国标码
机内码
字形码') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10863, 43, 188, 181, 64, 0, '点阵：汉字字形点阵的代码，16×16点阵、24×24点阵，32×32点阵，48×48点阵等', '点阵：汉字字形点阵的代码，16×16点阵、24×24点阵，32×32点阵，48×48点阵等

点阵：汉字字形点阵的代码，16×16点阵、24×24点阵，32×32点阵，48×48点阵等
编码、存储方式简单，无需转换直接输出，放大后产生的效果差，占用空间较大
2025/2/25
计算机信息表示及计算
64
汉字字形码：点阵式

点阵：汉字字形点阵的代码，16×16点阵、24×24点阵，32×32点阵，48×48点阵等
编码、存储方式简单，无需转换直接输出，放大后产生的效果差，占用空间较大
2025/2/25
计算机信息表示及计算
64
汉字字形码：点阵式', 'a3b76d4a585e4a101466f28f2a99ddebfbf74674845917ee6490d5fa479cad0c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '点阵：汉字字形点阵的代码，16×16点阵、24×24点阵，32×32点阵，48×48点阵等
编码、存储方式简单，无需转换直接输出，放大后产生的效果差，占用空间较大
2025/2/25
计算机信息表示及计算
64
汉字字形码：点阵式') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10864, 43, 188, 181, 65, 0, '汉字字形码：矢量式', '汉字字形码：矢量式

汉字字形码：矢量式
矢量：存储的是描述汉字字型的轮廓特征
使用数学公式描述字符的轮廓。矢量字形码并不直接记录每个像素的状态，而是记录字符轮廓的控制点和曲线。字体大小可以通过缩放公式调整，不会失去清晰度。
2025/2/25
计算机信息表示及计算
65
c:\windows\fonts目录下，如果文件扩展名为.FON，表示该文件为点阵字库，扩展名为.TTF则表示为矢量字库

汉字字形码：矢量式
矢量：存储的是描述汉字字型的轮廓特征
使用数学公式描述字符的轮廓。矢量字形码并不直接记录每个像素的状态，而是记录字符轮廓的控制点和曲线。字体大小可以通过缩放公式调整，不会失去清晰度。
2025/2/25
计算机信息表示及计算
65
c:\windows\fonts目录下，如果文件扩展名为.FON，表示该文件为点阵字库，扩展名为.TTF则表示为矢量字库', '69c3f8718ac11d56a165514e18f0da01b6009fbe1e15e950e65de0cc55983644', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '汉字字形码：矢量式
矢量：存储的是描述汉字字型的轮廓特征
使用数学公式描述字符的轮廓。矢量字形码并不直接记录每个像素的状态，而是记录字符轮廓的控制点和曲线。字体大小可以通过缩放公式调整，不会失去清晰度。
2025/2/25
计算机信息表示及计算
65
c:\windows\fonts目录下，如果文件扩展名为.FON，表示该文件为点阵字库，扩展名为.TTF则表示为矢量字库') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10865, 43, 188, 181, 66, 0, 'Unicode编码', 'Unicode编码

Unicode编码
Unicode 是一种统一的字符编码标准，旨在为全球几乎所有的书写系统分配唯一的代码点（编码）
与早期的编码标准（如 ASCII 或 GB2312）相比，Unicode 具有更广泛的适用性和灵活性，能够表示不同语言和符号的字符
"A" 的 Unicode 码位是 U+0041
"中" 的 Unicode 码位是 U+4E2D
"😊" 的 Unicode 码位是 U+1F60A
2025/2/25
计算机信息表示及计算
66

Unicode编码
Unicode 是一种统一的字符编码标准，旨在为全球几乎所有的书写系统分配唯一的代码点（编码）
与早期的编码标准（如 ASCII 或 GB2312）相比，Unicode 具有更广泛的适用性和灵活性，能够表示不同语言和符号的字符
"A" 的 Unicode 码位是 U+0041
"中" 的 Unicode 码位是 U+4E2D
"😊" 的 Unicode 码位是 U+1F60A…', 'b012dc845e0ae6c57f6557680d1969d56d8f7868100b1a175310fb8f37d5f615', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'Unicode编码
Unicode 是一种统一的字符编码标准，旨在为全球几乎所有的书写系统分配唯一的代码点（编码）
与早期的编码标准（如 ASCII 或 GB2312）相比，Unicode 具有更广泛的适用性和灵活性，能够表示不同语言和符号的字符
"A" 的 Unicode 码位是 U+0041
"中" 的 Unicode 码位是 U+4E2D
"😊" 的 Unicode 码位是 U+1F60A…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10866, 43, 188, 181, 67, 0, '字符串的序号', '字符串的序号

字符串的序号
正向递增序号 和 反向递减序号
反向递减序号
-12    -11 -10  -9 -8  -7 -6  -5 -4 -3 -2 -1


0	1	2	3	4	5	6	7	8	9	10 11
正向递增序号
2025/2/25
计算机信息表示及计算
67

字符串的序号
正向递增序号 和 反向递减序号
反向递减序号
-12    -11 -10  -9 -8  -7 -6  -5 -4 -3 -2 -1


0	1	2	3	4	5	6	7	8	9	10 11
正向递增序号
2025/2/25
计算机信息表示及计算
67', 'cafd3c483506df4cacd34c004620eebfc881f6b64a56526cc455ac26726bdc0d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串的序号
正向递增序号 和 反向递减序号
反向递减序号
-12    -11 -10  -9 -8  -7 -6  -5 -4 -3 -2 -1


0	1	2	3	4	5	6	7	8	9	10 11
正向递增序号
2025/2/25
计算机信息表示及计算
67') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10867, 43, 188, 181, 68, 0, '字符串的使用', '字符串的使用

字符串的使用
使用[ ]获取字符串中一个或多个字符

索引：返回字符串中单个字符	<字符串>[M]
"请输入带有符号的温度值: "[0]	或者	TempStr[-1]

切片：返回字符串中一段字符子串	<字符串>[M: N]
"请输入带有符号的温度值: "[1:3] 或者	TempStr[0:-1]
2025/2/25
计算机信息表示及计算
68

字符串的使用
使用[ ]获取字符串中一个或多个字符

索引：返回字符串中单个字符	<字符串>[M]
"请输入带有符号的温度值: "[0]	或者	TempStr[-1]

切片：返回字符串中一段字符子串	<字符串>[M: N]
"请输入带有符号的温度值: "[1:3] 或者	TempStr[0:-1]
2025/2/25
计算机信息表示及计算
68', 'd1cc0250c41a4b282d07f22cdb02f292110649ad42b20cf60ffbf31d474cf748', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串的使用
使用[ ]获取字符串中一个或多个字符

索引：返回字符串中单个字符	<字符串>[M]
"请输入带有符号的温度值: "[0]	或者	TempStr[-1]

切片：返回字符串中一段字符子串	<字符串>[M: N]
"请输入带有符号的温度值: "[1:3] 或者	TempStr[0:-1]
2025/2/25
计算机信息表示及计算
68') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10868, 43, 188, 181, 69, 0, '字符串切片用法', '字符串切片用法

字符串切片用法
格式<string>[<start>:<end>]
<string>[N:M]表示一个左闭右开区间[N,M)
<string>[0:4] 
<string>[2:-1] 
<string>[-3:]
<string>[:3]
2025/2/25
计算机信息表示及计算
69
<字符串>[M: N]，M缺失表示至开头，N缺失表示至结尾

字符串切片用法
格式<string>[<start>:<end>]
<string>[N:M]表示一个左闭右开区间[N,M)
<string>[0:4] 
<string>[2:-1] 
<string>[-3:]
<string>[:3]
2025/2/25
计算机信息表示及计算
69
<字符串>[M: N]，M缺失表示至开头，N缺失表示至结尾', '29e74aec56c3c2ac3cf45beb9a34509b5b4709cb4350b25d59d57ba7caa964c7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串切片用法
格式<string>[<start>:<end>]
<string>[N:M]表示一个左闭右开区间[N,M)
<string>[0:4] 
<string>[2:-1] 
<string>[-3:]
<string>[:3]
2025/2/25
计算机信息表示及计算
69
<字符串>[M: N]，M缺失表示至开头，N缺失表示至结尾') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10869, 43, 188, 181, 70, 0, '字符串切片高级用法', '字符串切片高级用法

字符串切片高级用法
使用[M: N: K]根据步长对字符串切片

<字符串>[M: N: K]，根据步长K对字符串切片
"〇一二三四五六七八九十"[1:8:2]	结果是	"一三五七"
"〇一二三四五六七八九十"[::-1]	结果是	"十九八七六五四三二一〇"
2025/2/25
计算机信息表示及计算
70

字符串切片高级用法
使用[M: N: K]根据步长对字符串切片

<字符串>[M: N: K]，根据步长K对字符串切片
"〇一二三四五六七八九十"[1:8:2]	结果是	"一三五七"
"〇一二三四五六七八九十"[::-1]	结果是	"十九八七六五四三二一〇"
2025/2/25
计算机信息表示及计算
70', 'd671a67a4743d3446f50c6cbcf805efa8854a81bbcd0e9329738e5e57d408541', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串切片高级用法
使用[M: N: K]根据步长对字符串切片

<字符串>[M: N: K]，根据步长K对字符串切片
"〇一二三四五六七八九十"[1:8:2]	结果是	"一三五七"
"〇一二三四五六七八九十"[::-1]	结果是	"十九八七六五四三二一〇"
2025/2/25
计算机信息表示及计算
70') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10870, 43, 188, 181, 71, 0, '字符串的特殊字符', '字符串的特殊字符

字符串的特殊字符
转义符 \

- 转义符表达特定字符的本意
"这里有个双引号(\")"
结果为	这里有个双引号(")
- 转义符形成一些组合，表达一些不可打印的含义
"\n"换行(光标移动到下行首)	"\r" 回车(光标移动到本行首)
类比快捷键
2025/2/25
计算机信息表示及计算
71

字符串的特殊字符
转义符 \

- 转义符表达特定字符的本意
"这里有个双引号(\")"
结果为	这里有个双引号(")
- 转义符形成一些组合，表达一些不可打印的含义
"\n"换行(光标移动到下行首)	"\r" 回车(光标移动到本行首)
类比快捷键
2025/2/25
计算机信息表示及计算
71', '5f70b411c55210f70aaa8618d45900e89dfef43728101b9618f3529d75c36daf', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串的特殊字符
转义符 \

- 转义符表达特定字符的本意
"这里有个双引号(\")"
结果为	这里有个双引号(")
- 转义符形成一些组合，表达一些不可打印的含义
"\n"换行(光标移动到下行首)	"\r" 回车(光标移动到本行首)
类比快捷键
2025/2/25
计算机信息表示及计算
71') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10871, 43, 188, 181, 72, 0, '字符串操作符', '字符串操作符

字符串操作符
由0个或多个字符组成的有序字符序列
2025/2/25
计算机信息表示及计算
72

字符串操作符
由0个或多个字符组成的有序字符序列
2025/2/25
计算机信息表示及计算
72', '05563f8669963de9e58572d8c5240182eee79942c491addb69cf9f25cb10dd78', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串操作符
由0个或多个字符组成的有序字符序列
2025/2/25
计算机信息表示及计算
72') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10872, 43, 188, 181, 73, 0, '获取星期字符串', '获取星期字符串

获取星期字符串

输入：1-7的整数，表示星期几

输出：输入整数对应的星期字符串

例如：输入3，输出 星期三
字符串操作符
2025/2/25
计算机信息表示及计算
73

获取星期字符串

输入：1-7的整数，表示星期几

输出：输入整数对应的星期字符串

例如：输入3，输出 星期三
字符串操作符
2025/2/25
计算机信息表示及计算
73', '8ae0259d7e6d0eff86160dfc3dd3aca456cd53269c723ce60d265447b7320307', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '获取星期字符串

输入：1-7的整数，表示星期几

输出：输入整数对应的星期字符串

例如：输入3，输出 星期三
字符串操作符
2025/2/25
计算机信息表示及计算
73') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10873, 43, 188, 181, 74, 0, '字符串操作符', '字符串操作符

字符串操作符
获取星期字符串

#WeekNamePrintV1.py
weekStr	=		"星期一星期二星期三星期四星期五星期六星期日"  weekId	=	eval(input("请输入星期数字(1-7)："))
pos	=	(weekId	–	1	)		*	3  print(weekStr[pos:	pos+3])
2025/2/25
计算机信息表示及计算
74

字符串操作符
获取星期字符串

#WeekNamePrintV1.py
weekStr	=		"星期一星期二星期三星期四星期五星期六星期日"  weekId	=	eval(input("请输入星期数字(1-7)："))
pos	=	(weekId	–	1	)		*	3  print(weekStr[pos:	pos+3])
2025/2/25
计算机信息表示及计算
74', 'b643968b5b7ad7198a40eb2d7d24893391962cf5e28f1d99ca669acff8ffaa71', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串操作符
获取星期字符串

#WeekNamePrintV1.py
weekStr	=		"星期一星期二星期三星期四星期五星期六星期日"  weekId	=	eval(input("请输入星期数字(1-7)："))
pos	=	(weekId	–	1	)		*	3  print(weekStr[pos:	pos+3])
2025/2/25
计算机信息表示及计算
74') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10874, 43, 188, 181, 75, 0, '字符串操作符', '字符串操作符

字符串操作符
获取星期字符串

#WeekNamePrintV2.py  
weekStr	=	"一二三四五六日"
weekId	=	eval(input("请输入星期数字(1-7)："))  print("星期"	+	weekStr[weekId-1])
2025/2/25
计算机信息表示及计算
75

字符串操作符
获取星期字符串

#WeekNamePrintV2.py  
weekStr	=	"一二三四五六日"
weekId	=	eval(input("请输入星期数字(1-7)："))  print("星期"	+	weekStr[weekId-1])
2025/2/25
计算机信息表示及计算
75', 'b802320ccd00da024c854a8613cca203c4e4f5c6ba9df09424d8a47cccd3cc63', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串操作符
获取星期字符串

#WeekNamePrintV2.py  
weekStr	=	"一二三四五六日"
weekId	=	eval(input("请输入星期数字(1-7)："))  print("星期"	+	weekStr[weekId-1])
2025/2/25
计算机信息表示及计算
75') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10875, 43, 188, 181, 76, 0, '字符串处理函数', '字符串处理函数

字符串处理函数
一些以函数形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
76

字符串处理函数
一些以函数形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
76', '191058c55ef9f69203c3ce5a6fc55d17f348f0ada322d983e3d80a90f93b01c2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理函数
一些以函数形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
76') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10876, 43, 188, 181, 77, 0, '字符串处理函数', '字符串处理函数

字符串处理函数
一些以函数形式提供的字符串处理功能
chr(u)
ord(x)
Unicode
单字符
2025/2/25
计算机信息表示及计算
77

字符串处理函数
一些以函数形式提供的字符串处理功能
chr(u)
ord(x)
Unicode
单字符
2025/2/25
计算机信息表示及计算
77', '64d4872ce5f3b880d9dc966dc0b369f71baff7023b24dadd93f0421422edf40e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理函数
一些以函数形式提供的字符串处理功能
chr(u)
ord(x)
Unicode
单字符
2025/2/25
计算机信息表示及计算
77') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10877, 43, 188, 181, 78, 0, 'Unicode编码', 'Unicode编码

Unicode编码
一些有趣的例子
>>>	"1	+	1	=	2	"	+	chr(10004)
''1	+	1	=	2	✔''
>>>	"这个字符♉的Unicode值是："	+	str(ord("♉"))
''这个字符♉的Unicode值是： 9801''
>>>	for	i	in	range(12):
print(chr(9800	+	i),	end="")
♈♉♊♋♌♍♎♏♐♑♒♓
2025/2/25
计算机信息表示及计算
78

Unicode编码
一些有趣的例子
>>>	"1	+	1	=	2	"	+	chr(10004)
''1	+	1	=	2	✔''
>>>	"这个字符♉的Unicode值是："	+	str(ord("♉"))
''这个字符♉的Unicode值是： 9801''
>>>	for	i	in	range(12):
print(chr(9800	+	i),	end="")
♈♉♊♋♌♍♎♏♐♑♒♓
2025/2/25…', 'e1595c8ef1671a0cc691b69662b1a3fe553ed191d80afa7622dacb2a2849deb2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'Unicode编码
一些有趣的例子
>>>	"1	+	1	=	2	"	+	chr(10004)
''1	+	1	=	2	✔''
>>>	"这个字符♉的Unicode值是："	+	str(ord("♉"))
''这个字符♉的Unicode值是： 9801''
>>>	for	i	in	range(12):
print(chr(9800	+	i),	end="")
♈♉♊♋♌♍♎♏♐♑♒♓
2025/2/25…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10878, 43, 188, NULL, 79, 0, '举例：凯撒密码实现', '举例：凯撒密码实现

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
C
F
原文
密文
67
70
+3
ord("C")
chr(70)
问题抽象
加密过程：字母对应的编码 + 3
解密过程：字母对应的编码 - 3
2025/2/25
计算机信息表示及计算
79

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
C
F
原文
密文
67
70
+3
ord("C")
…', 'f5608d8e7722b152c242e8208a344ad245f6bcbc6e82f40a5d711b2afa11e29b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
C
F
原文
密文
67
70
+3
ord("C")
…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10879, 43, 188, NULL, 80, 0, '举例：凯撒密码实现', '举例：凯撒密码实现

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
\\
原文
密文
89
92
+3
ord("Y")
chr(92)
2025/2/25
计算机信息表示及计算
80

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
\\
原文
密文
89
92
+3
ord("Y")…', '3e12b351bde0ca64c2be76351f988a265a346be00794d324141f5f4db90c24b2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
\\
原文
密文
89
92
+3
ord("Y")…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10880, 43, 188, NULL, 81, 0, '举例：凯撒密码实现', '举例：凯撒密码实现

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
B
原文
密文
89
66
ord("Y")
chr(92)
ord("A") + (ord("Y")- ord("A") + 3) % 26
2025/2/25
计算机信息表示及计算
81

举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
B
原文
密文
89
66
ord("Y")
chr…', 'e6fd53c607113f7c909acd21c79e5767717a0b1a236ccad06d627184ae8b5856', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '举例：凯撒密码实现
凯撒密码是古罗马凯撒大帝用来对军事情况进行加密的算法
其基本思想是：通过把字母移动三位来实现加密和解密
原文：A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
密文：D E F G H I J K L M N O P Q R S T U V W X Y Z A B C
Y
B
原文
密文
89
66
ord("Y")
chr…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10881, 43, 188, NULL, 82, 0, '举例：将单个大写字母加密', '举例：将单个大写字母加密

举例：将单个大写字母加密
character = input("请输入明文：")
password = chr(ord("A") + (ord(character) - ord("A") + 3) % 26)
print("密码为：")
print(password)
2025/2/25
计算机信息表示及计算
82

举例：将单个大写字母加密
character = input("请输入明文：")
password = chr(ord("A") + (ord(character) - ord("A") + 3) % 26)
print("密码为：")
print(password)
2025/2/25
计算机信息表示及计算
82', 'd4a8e1e7a1ffb9a2dbcd37a8a2a328345865816f3c861512c869286b8931c08e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '举例：将单个大写字母加密
character = input("请输入明文：")
password = chr(ord("A") + (ord(character) - ord("A") + 3) % 26)
print("密码为：")
print(password)
2025/2/25
计算机信息表示及计算
82') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10882, 43, 188, 181, 83, 0, '字符串处理方法', '字符串处理方法

字符串处理方法
"方法"在编程中是一个专有名词

"方法"特指<a>.<b>()风格中的函数<b>()

方法本身也是函数，但与<a>有关，<a>.<b>()风格使用

字符串或字符串变量是<a>，存在一些可用方法
2025/2/25
计算机信息表示及计算
83

字符串处理方法
"方法"在编程中是一个专有名词

"方法"特指<a>.<b>()风格中的函数<b>()

方法本身也是函数，但与<a>有关，<a>.<b>()风格使用

字符串或字符串变量是<a>，存在一些可用方法
2025/2/25
计算机信息表示及计算
83', 'b63d15783a9e58f61ee132cf6ae4042f5b22f49ab2ed21d07fbb22d8ed59590b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理方法
"方法"在编程中是一个专有名词

"方法"特指<a>.<b>()风格中的函数<b>()

方法本身也是函数，但与<a>有关，<a>.<b>()风格使用

字符串或字符串变量是<a>，存在一些可用方法
2025/2/25
计算机信息表示及计算
83') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10883, 43, 188, 181, 84, 0, '字符串处理方法', '字符串处理方法

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
84

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
84', 'db204485c35a8ebdb0ebf4d3a5c86c7c4b6ddd44c0c673f92b0f6551b6395daa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
84') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10884, 43, 188, 181, 85, 0, '字符串处理方法', '字符串处理方法

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
85

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
85', 'ee37bf03aa367c11737d84b2f8383d4863659d1d12bec29898279649aee0cd89', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
85') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10885, 43, 188, 181, 86, 0, '字符串处理方法', '字符串处理方法

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
86

字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
86', '25dcbdb91256e6d2f1a08eeafaa30ce790f55e82974d7b6730dcff90ab2feef2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '字符串处理方法
一些以方法形式提供的字符串处理功能
2025/2/25
计算机信息表示及计算
86') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10886, 43, 188, 181, 87, 0, '为什么会有字符串的格式化问题？', '为什么会有字符串的格式化问题？

为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
87

为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
87', 'be2bdab3bafbb8bb10d2b542257dfd723ca0ebfe71b17f0fd007e7366d2131aa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
87') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10887, 43, 188, 181, 88, 0, '为什么会有字符串的格式化问题？', '为什么会有字符串的格式化问题？

为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
88

为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
88', '9f50ce62f776de20f49a80ff70049c8d1c8462105951e7fc6c353723b864b7f5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '为什么会有字符串的格式化问题？
当需要同时输出字符串和变量/表达式，就需要字符串的格式化
输出方式一：输出变量/表达式
print(<变量>)
输出方式二：输出字符串
print(<字符串>)
2025/2/25
计算机信息表示及计算
88') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10888, 43, 188, 181, 89, 0, 'format()方法的基本使用', 'format()方法的基本使用

format()方法的基本使用
基本思路
日志：
2016-12-31：计算机PYTHON的CPU占用率为10%
2016-12-30：计算机PYTHON的CPU占用率为5%
2016-12-29：计算机OFFICE的CPU占用率为30%
变量A：计算机变量B的CPU占用率为变量C%
模板字符串
变量集
2025/2/25
计算机信息表示及计算
89

format()方法的基本使用
基本思路
日志：
2016-12-31：计算机PYTHON的CPU占用率为10%
2016-12-30：计算机PYTHON的CPU占用率为5%
2016-12-29：计算机OFFICE的CPU占用率为30%
变量A：计算机变量B的CPU占用率为变量C%
模板字符串
变量集
2025/2/25
计算机信息表示及计算
89', '230debd76afb8226d833b9250d29357f0852c9de573c02d2831f08dc206de179', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法的基本使用
基本思路
日志：
2016-12-31：计算机PYTHON的CPU占用率为10%
2016-12-30：计算机PYTHON的CPU占用率为5%
2016-12-29：计算机OFFICE的CPU占用率为30%
变量A：计算机变量B的CPU占用率为变量C%
模板字符串
变量集
2025/2/25
计算机信息表示及计算
89') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10889, 43, 188, 181, 90, 0, 'format()方法的基本使用', 'format()方法的基本使用

format()方法的基本使用
字符串format()方法的基本使用格式是：

print(“<模板字符串>”.format(<逗号分隔的参数>))
2025/2/25
计算机信息表示及计算
90
槽的默认顺序从0开始计数

format()方法的基本使用
字符串format()方法的基本使用格式是：

print(“<模板字符串>”.format(<逗号分隔的参数>))
2025/2/25
计算机信息表示及计算
90
槽的默认顺序从0开始计数', '2799ad8a2aeb1c30a84caca39501965c8e5417fa0ecf02f13738b3544dc6b2ed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法的基本使用
字符串format()方法的基本使用格式是：

print(“<模板字符串>”.format(<逗号分隔的参数>))
2025/2/25
计算机信息表示及计算
90
槽的默认顺序从0开始计数') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10890, 43, 188, 181, 91, 0, '槽的顺序', '槽的顺序

槽的顺序
槽
"{1}:计算机{0}的CPU占用率为{2}%".format("2020-10-22","C",10)
2025/2/25
计算机信息表示及计算
91

槽的顺序
槽
"{1}:计算机{0}的CPU占用率为{2}%".format("2020-10-22","C",10)
2025/2/25
计算机信息表示及计算
91', '65ced41e584f4e01f760f393806cd9f704a0e2efd0a587808343634dff9d216a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '槽的顺序
槽
"{1}:计算机{0}的CPU占用率为{2}%".format("2020-10-22","C",10)
2025/2/25
计算机信息表示及计算
91') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10891, 43, 188, 181, 92, 0, 'format()方法的格式控制', 'format()方法的格式控制

format()方法的格式控制
槽内部对格式化的配置方式

{ <参数序号> ： <格式控制标记>}
符号
^ 居中对齐
浮点数小数 精 度 或 字 符串最大输 出长度
整数类型
b, c, d, o, x, X
浮点数类型
e, E, f, %
：
<填充>	<对齐>
引导	用于填充的	< 左对齐 单个字符	> 右对齐
<宽度>	<,>
槽设定的输	数字的千位 出宽度		分隔符
<.精度>
<类型>
2025/2/25
计算机信息表示及计算
92

format()方法的格式控制
槽内部对格式化的配置方式

{ <参数序号> ： <格式控制标记>}
符号
^ 居中对齐
浮点数小数 精 度 或 字 符串最大输 出长度
整数类型
b, c, d, o, x, X
浮点数类型
e, E, f, %
：
<填充>	<对齐>
引导	用于填充的	< 左对齐 单个字符	> 右对齐
<宽度>	<,>
槽设定的输	数字的千位 出宽度		分隔符
<.精度>
<类…', '7f0f744adeb2ed471ceea43e57250232c693202edc7a3242ee9d17784858c20a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法的格式控制
槽内部对格式化的配置方式

{ <参数序号> ： <格式控制标记>}
符号
^ 居中对齐
浮点数小数 精 度 或 字 符串最大输 出长度
整数类型
b, c, d, o, x, X
浮点数类型
e, E, f, %
：
<填充>	<对齐>
引导	用于填充的	< 左对齐 单个字符	> 右对齐
<宽度>	<,>
槽设定的输	数字的千位 出宽度		分隔符
<.精度>
<类…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10892, 43, 188, 181, 93, 0, 'format()方法的格式控制', 'format()方法的格式控制

format()方法的格式控制
引导
^ 居中对齐
槽设定的输 出宽度
：
<填充>	<对齐>
用于填充的	< 左对齐 符号	单个字符	> 右对齐
<宽度>
<,>	<.精度>
<类型>
>>>"{0:=^20}".format("PYTHON")
''=======PYTHON=======''
>>>"{0:*>20}".format("DHU")
''*****************DHU''
>>>"{:10}".format("DHU")  ‘DHU	''
2025/2/25
计算机信息表示及计算
93

format()方法的格式控制
引导
^ 居中对齐
槽设定的输 出宽度
：
<填充>	<对齐>
用于填充的	< 左对齐 符号	单个字符	> 右对齐
<宽度>
<,>	<.精度>
<类型>
>>>"{0:=^20}".format("PYTHON")
''=======PYTHON=======''
>>>"{0:*>20}".format("DHU")
''*****************DHU''
>…', '64fda32b9b3ec862225cfba7afb3b72a0fa3fd1a53f0ec910136e6c09ff44e4d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法的格式控制
引导
^ 居中对齐
槽设定的输 出宽度
：
<填充>	<对齐>
用于填充的	< 左对齐 符号	单个字符	> 右对齐
<宽度>
<,>	<.精度>
<类型>
>>>"{0:=^20}".format("PYTHON")
''=======PYTHON=======''
>>>"{0:*>20}".format("DHU")
''*****************DHU''
>…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10893, 43, 188, 181, 94, 0, 'format()方法的格式控制', 'format()方法的格式控制

format()方法的格式控制
>>>"{0:,.2f}".format(12345.6789)
''12,345.68''
>>>"{0:b},{0:c},{0:d},{0:o},{0:x},{0:X}".format(425)
''110101001,Ʃ,425,651,1a9,1A9''
>>>"{0:e},{0:E},{0:f},{0:%}".format(3.14)  ''3.140000e+00,3.140000E+00,3.140000,314.000000%''
数字的千位 分隔符
浮点数小数 精 度 或 字 符串最大输 出长度
整数类型
b, c, d, o, x, X
浮点数类型
e, E, f, %
<填充>
<对齐>
<宽度>
<,>	<.精度>
<类型>
：
2025/2/25
计算机信息表示及计算
94

format()方法的格式控制
>>>"{0:,.2f}".format(12345.6789)
''12,345.68''
>>>"{0:b},{0:c},{0:d},{0:o},{0:x},{0:X}".format(425)
''110101001,Ʃ,425,651,1a9,1A9''
>>>"{0:e},{0:E},{0:f},{0:%}".format(3.14)  ''3.140000e+00…', '0d949444db041396b902c3f0cd3674a706054bbc25b63f62cef9e8cf79f3ed84', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法的格式控制
>>>"{0:,.2f}".format(12345.6789)
''12,345.68''
>>>"{0:b},{0:c},{0:d},{0:o},{0:x},{0:X}".format(425)
''110101001,Ʃ,425,651,1a9,1A9''
>>>"{0:e},{0:E},{0:f},{0:%}".format(3.14)  ''3.140000e+00…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10894, 43, 188, 181, 95, 0, 'format()方法并不改变原始值', 'format()方法并不改变原始值

format()方法并不改变原始值
2025/2/25
计算机信息表示及计算
95

format()方法并不改变原始值
2025/2/25
计算机信息表示及计算
95', 'e42674bd21b36bcb369c68cfde252a63dcb65f51438a99e3b28173bf63f7b79e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'format()方法并不改变原始值
2025/2/25
计算机信息表示及计算
95') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10895, 43, 188, NULL, 96, 0, '类型转换', '类型转换

类型转换
字符串类型转数字类型
int(s)
float(s)
eval(s)

数字类型转字符串类型
str()
2025/2/25
计算机信息表示及计算
96

类型转换
字符串类型转数字类型
int(s)
float(s)
eval(s)

数字类型转字符串类型
str()
2025/2/25
计算机信息表示及计算
96', 'd4c0a17389be5ae6f3217e193fcfef67a3f956d24f826df7310dcec6af12c046', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '类型转换
字符串类型转数字类型
int(s)
float(s)
eval(s)

数字类型转字符串类型
str()
2025/2/25
计算机信息表示及计算
96') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10896, 43, 188, NULL, 97, 0, 'eval()函数', 'eval()函数

eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
“慧眼识珠”
2025/2/25
计算机信息表示及计算
97
识别不出，则报错

eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
“慧眼识珠”
2025/2/25
计算机信息表示及计算
97
识别不出，则报错', '770a483fff461e207a1e75da517af6d47fca26bd96f9f93df9519beff029a8ed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', 'eval()函数
eval(<字符串>)函数将字符串转换为相应的数字类型或数字表达式
“慧眼识珠”
2025/2/25
计算机信息表示及计算
97
识别不出，则报错') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10897, 43, 188, NULL, 98, 0, '实例：个十百千（字符串版）', '实例：个十百千（字符串版）

实例：个十百千（字符串版）
2025/2/25
计算机信息表示及计算
98

实例：个十百千（字符串版）
2025/2/25
计算机信息表示及计算
98', '2f14549e0c19ae1c435e5fbeed87d3e6089cb8da59f47ba77b32c3395e5c4e15', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第三章：计算机信息表示及计算.pdf', '实例：个十百千（字符串版）
2025/2/25
计算机信息表示及计算
98') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10898, 43, 189, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10899, 43, 189, NULL, 2, 0, '第四章：流程自动化', '第四章：流程自动化

第四章：流程自动化

第四章：流程自动化', '6e4c996a4156dfaf7b72ff175e0f1c0306135f772ce6a103db0658a094a3a0a2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '第四章：流程自动化') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10900, 43, 189, NULL, 3, 0, '目录', '目录

目录
4.1、顺序结构
4.2、分支结构
4.3、循环结构
2024/9/16
流程自动化
3

目录
4.1、顺序结构
4.2、分支结构
4.3、循环结构
2024/9/16
流程自动化
3', 'a74100ea23cf1f96e7beec60ed72640fbbaeee5a62a88332b616c11c52fb1062', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '目录
4.1、顺序结构
4.2、分支结构
4.3、循环结构
2024/9/16
流程自动化
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10901, 43, 189, 182, 4, 0, '4.1、顺序结构', '4.1、顺序结构

4.1、顺序结构
2024/9/16
流程自动化
4

4.1、顺序结构
2024/9/16
流程自动化
4', '9cecb3711a9731c22db2857e2a6f41a3fc52282860498f7082de7904b05f08b6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '4.1、顺序结构
2024/9/16
流程自动化
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10902, 43, 189, 182, 5, 0, '顺序结构', '顺序结构

顺序结构
程序工作的一般流程为：
数据输入
运算处理
结果输出

顺序结构是指自上而下依次执行各条语句
2024/9/16
流程自动化
5

顺序结构
程序工作的一般流程为：
数据输入
运算处理
结果输出

顺序结构是指自上而下依次执行各条语句
2024/9/16
流程自动化
5', '6c269e7c7f78b56376a55bb312841dd4177f668d31419a6b3a5b2a6de4c56d17', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '顺序结构
程序工作的一般流程为：
数据输入
运算处理
结果输出

顺序结构是指自上而下依次执行各条语句
2024/9/16
流程自动化
5') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10903, 43, 189, 182, 6, 0, '举例：平均成绩计算', '举例：平均成绩计算

举例：平均成绩计算
编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位。
2024/9/16
流程自动化
6
练习：试试将float替换成eval

举例：平均成绩计算
编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位。
2024/9/16
流程自动化
6
练习：试试将float替换成eval', '217adb8d0ef9fc551c56768b9c681a514a96e10f3dc435ee87e50ad6107897c2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：平均成绩计算
编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位。
2024/9/16
流程自动化
6
练习：试试将float替换成eval') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10904, 43, 189, 182, 7, 0, '举例：圆周长和面积计算', '举例：圆周长和面积计算

举例：圆周长和面积计算
编写程序，从键盘输入圆的半径，计算并输出圆的周长和面积
2024/9/16
流程自动化
7

举例：圆周长和面积计算
编写程序，从键盘输入圆的半径，计算并输出圆的周长和面积
2024/9/16
流程自动化
7', '412567976e3f2ca0fcd6d628cb05a6e95bf6ef4bfaeeaa6350079c80ed51d8fd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：圆周长和面积计算
编写程序，从键盘输入圆的半径，计算并输出圆的周长和面积
2024/9/16
流程自动化
7') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10905, 43, 189, 182, 8, 0, '举例：年历输出', '举例：年历输出

举例：年历输出
编写程序，从键盘输入年份，输出当年的年历。
2024/9/16
流程自动化
8

举例：年历输出
编写程序，从键盘输入年份，输出当年的年历。
2024/9/16
流程自动化
8', 'fdade0470943e909d0d21111a943d6e7a9fd5ad2d4bbf6156f5130978962de28', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：年历输出
编写程序，从键盘输入年份，输出当年的年历。
2024/9/16
流程自动化
8') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10906, 43, 189, 182, 9, 0, '基本语句', '基本语句

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
2024/9/16
流程自动化
9

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
2024/9/16
流程自动化
9', 'baa4a51a35ce3ac928de6ef25c0d7fd5d10c75184fffc08784764629f6cf88c0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
2024/9/16
流程自动化
9') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10907, 43, 189, 182, 10, 0, '基本语句', '基本语句

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     a, b = b, a
2024/9/16
流程自动化
10

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     a, b = b, a
2024/9/16
流程自动化
10', 'c33fc70460942a3495f47c4b93ecb7e60355e97d5b32c09ce94a5d8dad930257', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     a, b = b, a
2024/9/16
流程自动化
10') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10908, 43, 189, 182, 11, 0, '基本语句', '基本语句

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
2024/9/16
流程自动化
11

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
2024/9/16
流程自动化
11', '584b83804f77f82c9d833ecb6ebf68121e6fdf88a4b1f89fde3b3d479b78ab16', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
2024/9/16
流程自动化
11') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10909, 43, 189, 182, 12, 0, '基本语句', '基本语句

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
（3）复合赋值  age += 3
2024/9/16
流程自动化
12

基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
（3）复合赋值  age += 3
2024/9/16
流程自动化
12', '434263e5defea2027c2a6491661b1fbec9232518985988db671421b6a0f0bbbd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '基本语句
1．赋值语句 a = 3
2．复合赋值语句
（1）序列赋值     x, y = 1, 1.5
（2）多目标赋值   first=second=third=“welcome”
（3）复合赋值  age += 3
2024/9/16
流程自动化
12') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10910, 43, 189, 182, 13, 0, '算术复合赋值运算符', '算术复合赋值运算符

算术复合赋值运算符
2024/9/16
流程自动化
13

算术复合赋值运算符
2024/9/16
流程自动化
13', 'abc730d55d18fb67aa36333d232eee89fb3cd0e37ac5b360634c6c630fd091e6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '算术复合赋值运算符
2024/9/16
流程自动化
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10911, 43, 189, 182, 14, 0, '4.2、分支结构', '4.2、分支结构

4.2、分支结构
2024/9/16
流程自动化
14

4.2、分支结构
2024/9/16
流程自动化
14', 'b282d8b772d3dcfa4511c053be12393e51feb40547103f6c009877b3c09b6749', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '4.2、分支结构
2024/9/16
流程自动化
14') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10912, 43, 189, 182, 15, 0, '分支结构', '分支结构

分支结构
分支结构是编程中常用的一种控制结构，用来根据条件的不同执行不同的代码路径


举例：考试成绩判断
如果 score 大于或等于 90，程序会输出 "优秀"。
如果 score 小于 90 但大于或等于 75，程序会输出 "良好"。
如果 score 小于 75 但大于或等于 60，程序会输出 "及格"。
如果 score 小于 60，程序会输出 "不及格"。
2024/9/16
流程自动化
15

分支结构
分支结构是编程中常用的一种控制结构，用来根据条件的不同执行不同的代码路径


举例：考试成绩判断
如果 score 大于或等于 90，程序会输出 "优秀"。
如果 score 小于 90 但大于或等于 75，程序会输出 "良好"。
如果 score 小于 75 但大于或等于 60，程序会输出 "及格"。
如果 score 小于 60，程序会输出 "不及格"。
2024/9/16
流程自动…', 'd83a00b30a9b8670371e04536ad2fc51ae904cb66e89678b0df35236877b6284', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '分支结构
分支结构是编程中常用的一种控制结构，用来根据条件的不同执行不同的代码路径


举例：考试成绩判断
如果 score 大于或等于 90，程序会输出 "优秀"。
如果 score 小于 90 但大于或等于 75，程序会输出 "良好"。
如果 score 小于 75 但大于或等于 60，程序会输出 "及格"。
如果 score 小于 60，程序会输出 "不及格"。
2024/9/16
流程自动…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10913, 43, 189, 182, 16, 0, '分支结构', '分支结构

分支结构
单分支结构
二分支结构
多分支结构
2024/9/16
流程自动化
16

分支结构
单分支结构
二分支结构
多分支结构
2024/9/16
流程自动化
16', '3153c25a9352dfea83d9a26ee4f329237f035bda68b2b8014534510498d47f3b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '分支结构
单分支结构
二分支结构
多分支结构
2024/9/16
流程自动化
16') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10914, 43, 189, 182, 17, 0, '单分支结构', '单分支结构

单分支结构
if语句
if 表达式:
    语句块
满足条件就执行某些语句块；
不满足条件就不执行某些语句块
缩进，用四个空格表示
2024/9/16
流程自动化
17

单分支结构
if语句
if 表达式:
    语句块
满足条件就执行某些语句块；
不满足条件就不执行某些语句块
缩进，用四个空格表示
2024/9/16
流程自动化
17', '3acd3eda94e6f37d24c9d243273a8f9a3d670fdfaf1a5b325bb71bb94ca6771c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '单分支结构
if语句
if 表达式:
    语句块
满足条件就执行某些语句块；
不满足条件就不执行某些语句块
缩进，用四个空格表示
2024/9/16
流程自动化
17') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10915, 43, 189, 182, 18, 0, '条件', '条件

条件
if语中<条件>部分可以使用任何能够产生True或False的语句（相当于问是不是）
形成判断条件最常见的方式是采用关系操作符
Python语言共有6个关系操作符
2024/9/16
流程自动化
18

条件
if语中<条件>部分可以使用任何能够产生True或False的语句（相当于问是不是）
形成判断条件最常见的方式是采用关系操作符
Python语言共有6个关系操作符
2024/9/16
流程自动化
18', '0e6379995ebab66373fd69f30e514d5489ae87a14a85deeffec1575421d64b15', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '条件
if语中<条件>部分可以使用任何能够产生True或False的语句（相当于问是不是）
形成判断条件最常见的方式是采用关系操作符
Python语言共有6个关系操作符
2024/9/16
流程自动化
18') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10916, 43, 189, 182, 19, 0, '举例：去网吧V1（单分支结构）', '举例：去网吧V1（单分支结构）

举例：去网吧V1（单分支结构）
2024/9/16
流程自动化
19

举例：去网吧V1（单分支结构）
2024/9/16
流程自动化
19', '5075511b31ee9e2413b07d4e7ec2c4a5d00b28b094b914d442110231e50835ae', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：去网吧V1（单分支结构）
2024/9/16
流程自动化
19') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10917, 43, 189, 182, 20, 0, '二分支结构', '二分支结构

二分支结构
if-else语句




<语句块1>是在if条件满足后执行的一个或多个语句序列
<语句块2>是if条件不满足后执行的语句序列
二分支语句用于区分<条件>的两种可能True或者False，分别形成执行路径
2024/9/16
流程自动化
20
if 表达式1:
    语句块1
else:
    语句块2
缩进，用四个空格表示

二分支结构
if-else语句




<语句块1>是在if条件满足后执行的一个或多个语句序列
<语句块2>是if条件不满足后执行的语句序列
二分支语句用于区分<条件>的两种可能True或者False，分别形成执行路径
2024/9/16
流程自动化
20
if 表达式1:
    语句块1
else:
    语句块2
缩进，用四个空格表示', 'aa1920ff02fbd0a8b89a0bca813f3d6735f855eeabcc7a38c034249b00b738d5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '二分支结构
if-else语句




<语句块1>是在if条件满足后执行的一个或多个语句序列
<语句块2>是if条件不满足后执行的语句序列
二分支语句用于区分<条件>的两种可能True或者False，分别形成执行路径
2024/9/16
流程自动化
20
if 表达式1:
    语句块1
else:
    语句块2
缩进，用四个空格表示') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10918, 43, 189, 182, 21, 0, '举例：去网吧V2（二分支结构）', '举例：去网吧V2（二分支结构）

举例：去网吧V2（二分支结构）
2024/9/16
流程自动化
21

举例：去网吧V2（二分支结构）
2024/9/16
流程自动化
21', 'ecc2bd3477dea15864fd03ab067cf4c9a4d49c5cda2d37cde663bcb3519716df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：去网吧V2（二分支结构）
2024/9/16
流程自动化
21') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10919, 43, 189, 182, 22, 0, '多分支结构', '多分支结构

多分支结构
2024/9/16
流程自动化
22
if 表达式1:
    语句块A
elif 表达式2:
    语句块B
elif 表达式3:
    语句块C
else:
    语句块D
缩进，用四个空格表示

多分支结构
2024/9/16
流程自动化
22
if 表达式1:
    语句块A
elif 表达式2:
    语句块B
elif 表达式3:
    语句块C
else:
    语句块D
缩进，用四个空格表示', '78e6440eba296627b847e539431e1896462be86154b48bb82a11a4a3e7522ec2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '多分支结构
2024/9/16
流程自动化
22
if 表达式1:
    语句块A
elif 表达式2:
    语句块B
elif 表达式3:
    语句块C
else:
    语句块D
缩进，用四个空格表示') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10920, 43, 189, 182, 23, 0, '条件严格按照顺序匹配', '条件严格按照顺序匹配

条件严格按照顺序匹配
2024/9/16
流程自动化
23

条件严格按照顺序匹配
2024/9/16
流程自动化
23', '71ce09c0fd3f625cde554d3cd7d986e2c742c88c85521afdf8f157554d855763', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '条件严格按照顺序匹配
2024/9/16
流程自动化
23') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10921, 43, 189, 182, 24, 0, '举例：去网吧V3（多分支结构）', '举例：去网吧V3（多分支结构）

举例：去网吧V3（多分支结构）
2024/9/16
流程自动化
24

举例：去网吧V3（多分支结构）
2024/9/16
流程自动化
24', '4d2184f6f4287c6a14f039e436d56354ab6e66ab36ce7e6ff32136f59b0c86e3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：去网吧V3（多分支结构）
2024/9/16
流程自动化
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10922, 43, 189, 182, 25, 0, '条件组合', '条件组合

条件组合
用于条件组合的三个保留字
2024/9/16
流程自动化
25

条件组合
用于条件组合的三个保留字
2024/9/16
流程自动化
25', 'cdd00a09160ce05b90328d04688c0e31d2e94bacaf70d7e3a24e6daf85042ce2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '条件组合
用于条件组合的三个保留字
2024/9/16
流程自动化
25') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10923, 43, 189, 182, 26, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
26
多条件判断（逻辑运算）
AND：一个为假，整体为假

2024/9/16
流程自动化
26
多条件判断（逻辑运算）
AND：一个为假，整体为假', '5c771c1ab51fe1b3c2bceac21a3b0164923c76b5643cf59d837e956476f6e595', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
26
多条件判断（逻辑运算）
AND：一个为假，整体为假') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10924, 43, 189, 182, 27, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
27
多条件判断（逻辑运算）
OR：一个为真，整体为真

2024/9/16
流程自动化
27
多条件判断（逻辑运算）
OR：一个为真，整体为真', '2d424b9fa852d9c9c92d9bc259a7be31876e3840daf7cd10e60d6aeda2a79ad2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
27
多条件判断（逻辑运算）
OR：一个为真，整体为真') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10925, 43, 189, 182, 28, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
28
多条件判断（逻辑运算）
NOT：非黑即白

2024/9/16
流程自动化
28
多条件判断（逻辑运算）
NOT：非黑即白', 'edd7b0476ee15dfe41db5fe3b493ef42ab93bee132de58ea2ff2f2e0c44785de', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
28
多条件判断（逻辑运算）
NOT：非黑即白') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10926, 43, 189, 182, 29, 0, '判断条件真假', '判断条件真假

判断条件真假
5%3==1 and 5 < 3+8
5>=3*1.6 and 7*2 <= 9
20/5 > 4 or 5//2 < 2.1
5>=3 or 7 <= 9
not 3>5
3 < 5 and 5 > 3 or 5 > 4
2024/9/16
流程自动化
29
互动：多条件判断（逻辑运算）
AND：一个为假，整体为假
OR：一个为真，整体为真
NOT：非黑即白

判断条件真假
5%3==1 and 5 < 3+8
5>=3*1.6 and 7*2 <= 9
20/5 > 4 or 5//2 < 2.1
5>=3 or 7 <= 9
not 3>5
3 < 5 and 5 > 3 or 5 > 4
2024/9/16
流程自动化
29
互动：多条件判断（逻辑运算）
AND：一个为假，整体为假
OR：一个为真，整体为真
NOT：非黑即白', '29faf23d085be11a63e759c46d01539c9e9da564eede7668804eceff031b3eb7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '判断条件真假
5%3==1 and 5 < 3+8
5>=3*1.6 and 7*2 <= 9
20/5 > 4 or 5//2 < 2.1
5>=3 or 7 <= 9
not 3>5
3 < 5 and 5 > 3 or 5 > 4
2024/9/16
流程自动化
29
互动：多条件判断（逻辑运算）
AND：一个为假，整体为假
OR：一个为真，整体为真
NOT：非黑即白') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10927, 43, 189, 182, 30, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
30
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:

2024/9/16
流程自动化
30
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:', '90d5163cb8827f7c678b4f876f22dbcf60e8d38b760662b69948576589d2fa27', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
30
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10928, 43, 189, 182, 31, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
31
多条件判断（逻辑运算）
if <条件一> or <条件二> or <条件三>:

2024/9/16
流程自动化
31
多条件判断（逻辑运算）
if <条件一> or <条件二> or <条件三>:', '738cd6d6719c5ce65274c0a8d633d126feddbf3388ce8f74fe1324f2cde24da6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
31
多条件判断（逻辑运算）
if <条件一> or <条件二> or <条件三>:') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10929, 43, 189, 182, 32, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
32
多条件判断（逻辑运算）
if not <条件一>:
在校的非应届本科毕业生不得报考
如果  not <是在校的非应届本科毕业生>：
         才可报考

2024/9/16
流程自动化
32
多条件判断（逻辑运算）
if not <条件一>:
在校的非应届本科毕业生不得报考
如果  not <是在校的非应届本科毕业生>：
         才可报考', '21b9f8e32b5ac0bf5e5a41d7b6e9459be7c40cdc6d3e80bdee9e4dba492c7221', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
32
多条件判断（逻辑运算）
if not <条件一>:
在校的非应届本科毕业生不得报考
如果  not <是在校的非应届本科毕业生>：
         才可报考') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10930, 43, 189, 182, 33, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
33
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:
if <条件一> or <条件二> or <条件三>:
if not <条件一>:
if <条件一> and <条件二> or <条件三>:

2024/9/16
流程自动化
33
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:
if <条件一> or <条件二> or <条件三>:
if not <条件一>:
if <条件一> and <条件二> or <条件三>:', 'cfd87652a6a79c280b1f1874349992e3ac1dbf1930152c0bc9c4b05a4fbe54e2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
33
多条件判断（逻辑运算）
if <条件一> and <条件二> and <条件三>:
if <条件一> or <条件二> or <条件三>:
if not <条件一>:
if <条件一> and <条件二> or <条件三>:') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10931, 43, 189, 182, 34, 0, '举例：三角形判断（多条件判断）', '举例：三角形判断（多条件判断）

举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
34
组成三角形的条件：任意两边之和大于第三边

思考：如何用计算机来表示这个条件？

举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
34
组成三角形的条件：任意两边之和大于第三边

思考：如何用计算机来表示这个条件？', 'f106178a8a57eb5ae881be2435a8a8f1ed925ce2a0881244c6da4460925a4800', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
34
组成三角形的条件：任意两边之和大于第三边

思考：如何用计算机来表示这个条件？') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10932, 43, 189, 182, 35, 0, '举例：三角形判断（多条件判断）', '举例：三角形判断（多条件判断）

举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
35

举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
35', '153e4e7c7c51def58cb6a26081e42ff48cc5bd0f0ff55ed537da4c9ebcc206be', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：三角形判断（多条件判断）
编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
2024/9/16
流程自动化
35') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10933, 43, 189, 182, 36, 0, '分支语句嵌套', '分支语句嵌套

分支语句嵌套
当有多个条件需要满足并且条件之间有递进关系时，可以使用分支语句的嵌套。
其中，if子句、elif子句以及else子句中都可以嵌套if语句或者if-elif-else子句
2024/9/16
流程自动化
36

分支语句嵌套
当有多个条件需要满足并且条件之间有递进关系时，可以使用分支语句的嵌套。
其中，if子句、elif子句以及else子句中都可以嵌套if语句或者if-elif-else子句
2024/9/16
流程自动化
36', '99e0d9a3b085db16be6752670d556e97e1e5ea2af8cc99fb024c8511056ace2b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '分支语句嵌套
当有多个条件需要满足并且条件之间有递进关系时，可以使用分支语句的嵌套。
其中，if子句、elif子句以及else子句中都可以嵌套if语句或者if-elif-else子句
2024/9/16
流程自动化
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10934, 43, 189, 182, 37, 0, '举例：用户登录', '举例：用户登录

举例：用户登录
编写程序，实现一个用户登录系统
2024/9/16
流程自动化
37

举例：用户登录
编写程序，实现一个用户登录系统
2024/9/16
流程自动化
37', '7237e2a88c70e86a63603956c4fa11d81d422ea70ac40ec8d2387c8342844ca4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：用户登录
编写程序，实现一个用户登录系统
2024/9/16
流程自动化
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10935, 43, 189, 182, 38, 0, '举例：用户登录（解题思路）', '举例：用户登录（解题思路）

举例：用户登录（解题思路）
从键盘输入用户名和密码，先判断用户名再判断密码
如果用户名不正确，则直接提示用户名输入有误；
如果用户名正确，则进一步判断密码，并给出判断结果的提示
2024/9/16
流程自动化
38

举例：用户登录（解题思路）
从键盘输入用户名和密码，先判断用户名再判断密码
如果用户名不正确，则直接提示用户名输入有误；
如果用户名正确，则进一步判断密码，并给出判断结果的提示
2024/9/16
流程自动化
38', '44fde475b7eaad5d8222d9f9617098a2f20ea0e4200b9aba79722a6a7b2edd6e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：用户登录（解题思路）
从键盘输入用户名和密码，先判断用户名再判断密码
如果用户名不正确，则直接提示用户名输入有误；
如果用户名正确，则进一步判断密码，并给出判断结果的提示
2024/9/16
流程自动化
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10936, 43, 189, 182, 39, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
39
举例：用户登录（嵌套分支语句）
抽象
自动化

2024/9/16
流程自动化
39
举例：用户登录（嵌套分支语句）
抽象
自动化', '778fd29c7ba6181cc41813decf0f93c6824e16f18cdb4f9339ad9041dac7344c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
39
举例：用户登录（嵌套分支语句）
抽象
自动化') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10937, 43, 189, 182, 40, 0, '问题描述：刘昊然今年能不能结婚？', '问题描述：刘昊然今年能不能结婚？

问题描述：刘昊然今年能不能结婚？
2024/9/16
流程自动化
40
举例：合法结婚年龄判断

问题描述：刘昊然今年能不能结婚？
2024/9/16
流程自动化
40
举例：合法结婚年龄判断', 'e7ad4037ac81825935f01a708cc5fc133fd204366abbec67062a5ae4af128248', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '问题描述：刘昊然今年能不能结婚？
2024/9/16
流程自动化
40
举例：合法结婚年龄判断') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10938, 43, 189, 182, 41, 0, '举例：合法结婚年龄判断（解题思路）', '举例：合法结婚年龄判断（解题思路）

举例：合法结婚年龄判断（解题思路）
我国的婚姻法规定，男性22岁为合法结婚年龄，女性20岁为合法结婚年龄。
因此如果要判断一个人是否到了合法结婚年龄，首先需要使用双分支结构判断性别，再用递进的双分支结构判断年龄，并输出判断结果。
2024/9/16
流程自动化
41

举例：合法结婚年龄判断（解题思路）
我国的婚姻法规定，男性22岁为合法结婚年龄，女性20岁为合法结婚年龄。
因此如果要判断一个人是否到了合法结婚年龄，首先需要使用双分支结构判断性别，再用递进的双分支结构判断年龄，并输出判断结果。
2024/9/16
流程自动化
41', '68581368333b9150587dfc0f528d36517bd630aa214ea8bc9d24530180ff4e23', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：合法结婚年龄判断（解题思路）
我国的婚姻法规定，男性22岁为合法结婚年龄，女性20岁为合法结婚年龄。
因此如果要判断一个人是否到了合法结婚年龄，首先需要使用双分支结构判断性别，再用递进的双分支结构判断年龄，并输出判断结果。
2024/9/16
流程自动化
41') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10939, 43, 189, 182, 42, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
42
举例：合法结婚年龄（嵌套分支语句）

2024/9/16
流程自动化
42
举例：合法结婚年龄（嵌套分支语句）', '34d4527064c4ebdf0c609cdcf54d21d24b9dfd7f3e45eabe8c99d8d9ea366ffc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
42
举例：合法结婚年龄（嵌套分支语句）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10940, 43, 189, 182, 43, 0, '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。

编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。
2024/9/16
流程自动化
43
举例：计算器（多分支+嵌套）

编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。
2024/9/16
流程自动化
43
举例：计算器（多分支+嵌套）', 'f38fdfcdc8c1e26479fc6c0315423368aa5cc283e3fbe3cb25d98df07c3489de', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。
2024/9/16
流程自动化
43
举例：计算器（多分支+嵌套）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10941, 43, 189, 182, 44, 0, '举例：BMI 指标计算', '举例：BMI 指标计算

举例：BMI 指标计算
多个不同的分支语句
2024/9/16
流程自动化
44
BMI的定义如下：
BMI = 体重（kg）÷身高2（m2）
例如，一个人身高1.75米、体重75公斤，他的BMI值为24.49

举例：BMI 指标计算
多个不同的分支语句
2024/9/16
流程自动化
44
BMI的定义如下：
BMI = 体重（kg）÷身高2（m2）
例如，一个人身高1.75米、体重75公斤，他的BMI值为24.49', '409f8dc1cb76355f989503e143dcbfb5983fa8b0ba7921d544b8cc1e1b752a0e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：BMI 指标计算
多个不同的分支语句
2024/9/16
流程自动化
44
BMI的定义如下：
BMI = 体重（kg）÷身高2（m2）
例如，一个人身高1.75米、体重75公斤，他的BMI值为24.49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10942, 43, 189, 182, 45, 0, '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。

编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。
2024/9/16
流程自动化
45
举例：猜数字（random+分支）

编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。
2024/9/16
流程自动化
45
举例：猜数字（random+分支）', '472757ba5d3e90c63595e20a81bdef3357af0686aa2eef51e4c5420526dc87e8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。
2024/9/16
流程自动化
45
举例：猜数字（random+分支）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10943, 43, 189, 182, 46, 0, '4.3、循环结构', '4.3、循环结构

4.3、循环结构
2024/9/16
流程自动化
46

4.3、循环结构
2024/9/16
流程自动化
46', 'cb487aa218761dfd9ad4e5513e677ea7f7457dc03c362cc06755ebcaeab1207b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '4.3、循环结构
2024/9/16
流程自动化
46') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10944, 43, 189, 182, 47, 0, '循环结构', '循环结构

循环结构
根据循环执行次数的确定性，循环可以分为确定次数循环和非确定次数循环。
确定次数循环
for循环
非确定次数循环
while循环
2024/9/16
流程自动化
47

循环结构
根据循环执行次数的确定性，循环可以分为确定次数循环和非确定次数循环。
确定次数循环
for循环
非确定次数循环
while循环
2024/9/16
流程自动化
47', 'cc8a65172aef2238062564a488e9395202cba02774aa5f8d13e0971381799a3d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '循环结构
根据循环执行次数的确定性，循环可以分为确定次数循环和非确定次数循环。
确定次数循环
for循环
非确定次数循环
while循环
2024/9/16
流程自动化
47') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10945, 43, 189, 182, 48, 0, '遍历循环: for语句', '遍历循环: for语句

遍历循环: for语句
确定次数循环指循环体对循环次数有明确的定义
遍历循环：采用遍历结构（对象）中元素个数来体现
Python通过保留字for实现“遍历循环” ：
2024/9/16
流程自动化
48
for 循环变量 in 对象:
      语句块A
else:  #可选
      语句块B

遍历循环: for语句
确定次数循环指循环体对循环次数有明确的定义
遍历循环：采用遍历结构（对象）中元素个数来体现
Python通过保留字for实现“遍历循环” ：
2024/9/16
流程自动化
48
for 循环变量 in 对象:
      语句块A
else:  #可选
      语句块B', '90652921277cf71c3c257af387c503e708c529927c7717674ba119c23e272b55', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '遍历循环: for语句
确定次数循环指循环体对循环次数有明确的定义
遍历循环：采用遍历结构（对象）中元素个数来体现
Python通过保留字for实现“遍历循环” ：
2024/9/16
流程自动化
48
for 循环变量 in 对象:
      语句块A
else:  #可选
      语句块B') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10946, 43, 189, 182, 49, 0, '遍历循环: for语句', '遍历循环: for语句

遍历循环: for语句
遍历结构可以是字符串、文件、组合数据类型或range()函数：
2024/9/16
流程自动化
49

遍历循环: for语句
遍历结构可以是字符串、文件、组合数据类型或range()函数：
2024/9/16
流程自动化
49', 'ebcc13d91dd0d684710f65d0a5c32f37891c8dc3b33ac662fde80919808e5244', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '遍历循环: for语句
遍历结构可以是字符串、文件、组合数据类型或range()函数：
2024/9/16
流程自动化
49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10947, 43, 189, 182, 50, 0, '内建函数range()用于生成整数序列', '内建函数range()用于生成整数序列

内建函数range()用于生成整数序列
通常的写法是：range(start, end, step)
左闭右开区间[start, end)
step代表序列的步长（可以省略，默认值是1）
2024/9/16
流程自动化
50
for + range()函数
输出：

内建函数range()用于生成整数序列
通常的写法是：range(start, end, step)
左闭右开区间[start, end)
step代表序列的步长（可以省略，默认值是1）
2024/9/16
流程自动化
50
for + range()函数
输出：', '700375ec8d31f738a1bcf6e8d7c3f630454b865710d087bad43543ae1133f7ed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '内建函数range()用于生成整数序列
通常的写法是：range(start, end, step)
左闭右开区间[start, end)
step代表序列的步长（可以省略，默认值是1）
2024/9/16
流程自动化
50
for + range()函数
输出：') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10948, 43, 189, 182, 51, 0, '举例：自然数之和', '举例：自然数之和

举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
51
数学思维（公式运算）
计算思维（穷举运算）
特点：
规则可能很复杂, 但计算量却可能很小
人需要知道具体的计算规则
特定的规则
从 1 累加到 10000
特点：
规则可能很简单, 但计算量却很大
机器也可以采用人所使用的计算规则
一般性的规则

举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
51
数学思维（公式运算）
计算思维（穷举运算）
特点：
规则可能很复杂, 但计算量却可能很小
人需要知道具体的计算规则
特定的规则
从 1 累加到 10000
特点：
规则可能很简单, 但计算量却很大
机器也可以采用人所使用的计算规则
一般性的规则', '5918d89ed690e88628e4eef16359010e429d982518de9d749b61aceac4ccd53a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
51
数学思维（公式运算）
计算思维（穷举运算）
特点：
规则可能很复杂, 但计算量却可能很小
人需要知道具体的计算规则
特定的规则
从 1 累加到 10000
特点：
规则可能很简单, 但计算量却很大
机器也可以采用人所使用的计算规则
一般性的规则') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10949, 43, 189, 182, 52, 0, '举例：自然数之和', '举例：自然数之和

举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
52

举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
52', '6b8ead971f6f0b083f2434add29f9e048f7991117804089d7439743766f975d1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。
2024/9/16
流程自动化
52') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10950, 43, 189, 182, 53, 0, '举例：自然数偶数之和', '举例：自然数偶数之和

举例：自然数偶数之和
编写程序，使用for语句计算1～10000的偶数自然数之和。
2024/9/16
流程自动化
53
扩展思考：所有质数之和？

举例：自然数偶数之和
编写程序，使用for语句计算1～10000的偶数自然数之和。
2024/9/16
流程自动化
53
扩展思考：所有质数之和？', '0134c54ced92ec0dbeee61b3be616501cc4a3d7e6386f928e02d62f95f680661', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：自然数偶数之和
编写程序，使用for语句计算1～10000的偶数自然数之和。
2024/9/16
流程自动化
53
扩展思考：所有质数之和？') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10951, 43, 189, 182, 54, 0, 'for+字符串', 'for+字符串

for+字符串
字符串可以看作是字符的序列
每个字符作为一个循环变量
2024/9/16
流程自动化
54

for+字符串
字符串可以看作是字符的序列
每个字符作为一个循环变量
2024/9/16
流程自动化
54', 'b9f7d7120b163836033cf2cc039bf7447e6b3b69e896b459acd56aed007c7e8b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', 'for+字符串
字符串可以看作是字符的序列
每个字符作为一个循环变量
2024/9/16
流程自动化
54') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10952, 43, 189, 182, 55, 0, '无限循环: while语句', '无限循环: while语句

无限循环: while语句
无限循环一直保持循环操作直到特定循环条件不被满足才结束，不需要提前知道确定循环次数。
Python通过保留字while实现无限循环，使用方法如下：
2024/9/16
流程自动化
55
初始值 
while 表达式:  #表达式的值为真
      语句块A	  #符合条件时执行的语句
else:       	  #可选项
      语句块B
if分支结构
while循环结构

无限循环: while语句
无限循环一直保持循环操作直到特定循环条件不被满足才结束，不需要提前知道确定循环次数。
Python通过保留字while实现无限循环，使用方法如下：
2024/9/16
流程自动化
55
初始值 
while 表达式:  #表达式的值为真
      语句块A	  #符合条件时执行的语句
else:       	  #可选项
      语句块B
if分支结构
whil…', 'b5a1b20915ca7bbd76cca47910be5c2d6d86badc2ea0f2f372119dd4f042384a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '无限循环: while语句
无限循环一直保持循环操作直到特定循环条件不被满足才结束，不需要提前知道确定循环次数。
Python通过保留字while实现无限循环，使用方法如下：
2024/9/16
流程自动化
55
初始值 
while 表达式:  #表达式的值为真
      语句块A	  #符合条件时执行的语句
else:       	  #可选项
      语句块B
if分支结构
whil…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10953, 43, 189, 182, 56, 0, '举例：计算π的近似值', '举例：计算π的近似值

举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
56

举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
56', '1cc00beaa948ddf9a3b2180546bd2563f186d32b5fea82f9643543a084cb3510', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
56') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10954, 43, 189, 182, 57, 0, '举例：计算π的近似值', '举例：计算π的近似值

举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
57

举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
57', '9e8b70ac597b6d4291eeffcd8a7ab27f46c18b29cc151c28b9f916417d939cc3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：计算π的近似值
用莱布尼茨公式计算π的近似值
2024/9/16
流程自动化
57') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10955, 43, 189, 182, 58, 0, '举例：计算π的近似值', '举例：计算π的近似值

举例：计算π的近似值
编写程序，用莱布尼茨公式计算π的近似值，直到最后一项的绝对值小于10-3为止
2024/9/16
流程自动化
58

举例：计算π的近似值
编写程序，用莱布尼茨公式计算π的近似值，直到最后一项的绝对值小于10-3为止
2024/9/16
流程自动化
58', '8f0951532270e84eb7233f5736e9c56ab05dbb98d60c137982b62e3051e129d9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：计算π的近似值
编写程序，用莱布尼茨公式计算π的近似值，直到最后一项的绝对值小于10-3为止
2024/9/16
流程自动化
58') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10956, 43, 189, 182, 59, 0, '死循环', '死循环

死循环
在while循环中，如果表达式的值恒真，循环将一直执行下去，无法靠自身终止，从而产生死循环
2024/9/16
流程自动化
59

死循环
在while循环中，如果表达式的值恒真，循环将一直执行下去，无法靠自身终止，从而产生死循环
2024/9/16
流程自动化
59', 'eeb4dee453e6be3b6401b9d03d06ee46cc14ac05c19ab23ad7029543532c1ded', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '死循环
在while循环中，如果表达式的值恒真，循环将一直执行下去，无法靠自身终止，从而产生死循环
2024/9/16
流程自动化
59') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10957, 43, 189, 182, 60, 0, '循环 + 分支语句嵌套', '循环 + 分支语句嵌套

循环 + 分支语句嵌套
2024/9/16
流程自动化
60

循环 + 分支语句嵌套
2024/9/16
流程自动化
60', '30bed2633b15713f5edcac26d67c839174812042b4e6e310472d9b7775b44e7d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '循环 + 分支语句嵌套
2024/9/16
流程自动化
60') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10958, 43, 189, 182, 61, 0, '循环 + 分支语句嵌套', '循环 + 分支语句嵌套

循环 + 分支语句嵌套
2024/9/16
流程自动化
61
默认情况下
print(“”, end = “\n”)
不换行
print(“”, end = “”)

循环 + 分支语句嵌套
2024/9/16
流程自动化
61
默认情况下
print(“”, end = “\n”)
不换行
print(“”, end = “”)', '167e232950da35537d3856d7fd0a750452229205f5fd57e2b58dd5895f6359e6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '循环 + 分支语句嵌套
2024/9/16
流程自动化
61
默认情况下
print(“”, end = “\n”)
不换行
print(“”, end = “”)') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10959, 43, 189, 182, 62, 0, '举例：凯撒密码实现', '举例：凯撒密码实现

举例：凯撒密码实现
多个小写字母加密
2024/9/16
流程自动化
62

举例：凯撒密码实现
多个小写字母加密
2024/9/16
流程自动化
62', '1cff3795305d030e55699f3705fc96ff942b1ed491089838de79196462c6136f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：凯撒密码实现
多个小写字母加密
2024/9/16
流程自动化
62') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10960, 43, 189, 182, 63, 0, '循环语句嵌套', '循环语句嵌套

循环语句嵌套
为了解决复杂的问题，可以使用循环语句的嵌套，嵌套层数不限，但是循环的内外层之间不能交叉。其中，双层循环是一种常用的循环嵌套，循环的总次数等于内外层次数之积
2024/9/16
流程自动化
63

循环语句嵌套
为了解决复杂的问题，可以使用循环语句的嵌套，嵌套层数不限，但是循环的内外层之间不能交叉。其中，双层循环是一种常用的循环嵌套，循环的总次数等于内外层次数之积
2024/9/16
流程自动化
63', 'db87159c9ffd3eecc6d5ea375c6cc40e5b27fe9c12cda711bc7e0c7ece2f233f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '循环语句嵌套
为了解决复杂的问题，可以使用循环语句的嵌套，嵌套层数不限，但是循环的内外层之间不能交叉。其中，双层循环是一种常用的循环嵌套，循环的总次数等于内外层次数之积
2024/9/16
流程自动化
63') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10961, 43, 189, 182, 64, 0, '举例：九九乘法表', '举例：九九乘法表

举例：九九乘法表
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
64

举例：九九乘法表
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
64', 'cfa032a4ca54311fd5353330f9f7954d59516f5d72cde9c6adc40e2f960ab755', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：九九乘法表
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
64') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10962, 43, 189, 182, 65, 0, '举例：九九乘法表(2)', '举例：九九乘法表(2)

举例：九九乘法表(2)
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
65

举例：九九乘法表(2)
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
65', 'c04b9cbea4fb007ad973a143c119f1006f23a47b30cc1d8910daf14a61ec200c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：九九乘法表(2)
编写程序，使用双重循环输出九九乘法表
2024/9/16
流程自动化
65') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10963, 43, 189, 182, 66, 0, '举例：三角形图案', '举例：三角形图案

举例：三角形图案
编写程序，使用双重循环输出所示三角形图案
2024/9/16
流程自动化
66

举例：三角形图案
编写程序，使用双重循环输出所示三角形图案
2024/9/16
流程自动化
66', '8319bb56df7630e3c6c266fc0d7420c68ec69560c43078006d4139475cce27c8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '举例：三角形图案
编写程序，使用双重循环输出所示三角形图案
2024/9/16
流程自动化
66') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10964, 43, 189, 182, 67, 0, '中断循环', '中断循环

中断循环
中断当前循环break
中断本次循环continue
2024/9/16
流程自动化
67

中断循环
中断当前循环break
中断本次循环continue
2024/9/16
流程自动化
67', '19f9fbadd2402f197f4801c5fdefc5a7b293deb6dacab3ef61cc0e08335573c7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '中断循环
中断当前循环break
中断本次循环continue
2024/9/16
流程自动化
67') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10965, 43, 189, 182, 68, 0, '循环 + 分支 + break/continue语句嵌套', '循环 + 分支 + break/continue语句嵌套

循环 + 分支 + break/continue语句嵌套
2024/9/16
流程自动化
68

循环 + 分支 + break/continue语句嵌套
2024/9/16
流程自动化
68', '12f38e08b06129befa1b33ad7433fe1478f5e30dac6f59b8df53a8630ad97354', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '循环 + 分支 + break/continue语句嵌套
2024/9/16
流程自动化
68') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10966, 43, 189, 182, 69, 0, '转移和中断语句', '转移和中断语句

转移和中断语句
break语句
break语句用于中断当前循环的执行，跳出循环结构
对于包含else子句的while循环和for循环而言，在while或for子句中一旦执行break语句，else子句将没有机会执行
Continue语句
与break语句不同，continue语句用于中断本次循环的执行，进入下一轮循环条件是否满足的判断
2024/9/16
流程自动化
69

转移和中断语句
break语句
break语句用于中断当前循环的执行，跳出循环结构
对于包含else子句的while循环和for循环而言，在while或for子句中一旦执行break语句，else子句将没有机会执行
Continue语句
与break语句不同，continue语句用于中断本次循环的执行，进入下一轮循环条件是否满足的判断
2024/9/16
流程自动化
69', '85362a63b070b5465ce52eb68bf5f7f14c07cc8ec92fa493cf4192e3b75f8273', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '转移和中断语句
break语句
break语句用于中断当前循环的执行，跳出循环结构
对于包含else子句的while循环和for循环而言，在while或for子句中一旦执行break语句，else子句将没有机会执行
Continue语句
与break语句不同，continue语句用于中断本次循环的执行，进入下一轮循环条件是否满足的判断
2024/9/16
流程自动化
69') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10967, 43, 189, 182, 70, 0, '半路循环', '半路循环

半路循环
前面介绍过死循环的概念，在死循环程序中，通过添加break语句终止程序的执行，称为半路循环
2024/9/16
流程自动化
70

半路循环
前面介绍过死循环的概念，在死循环程序中，通过添加break语句终止程序的执行，称为半路循环
2024/9/16
流程自动化
70', 'f6755c0052a54d32edf4acd740986db1bc6ddf4677050bed6bfcd13cfb02cb92', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '半路循环
前面介绍过死循环的概念，在死循环程序中，通过添加break语句终止程序的执行，称为半路循环
2024/9/16
流程自动化
70') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10968, 43, 189, 182, 71, 0, '编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。', '编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。

编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。
2024/9/16
流程自动化
71
举例：重复输入密码

编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。
2024/9/16
流程自动化
71
举例：重复输入密码', '48feaad72df8fb2d2f059cf2510643bacab82e6d7b37928b7fef74c024a31245', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。
2024/9/16
流程自动化
71
举例：重复输入密码') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10969, 43, 189, 182, 72, 0, '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完

编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完
2024/9/16
流程自动化
72
举例：猜数字V1

编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完
2024/9/16
流程自动化
72
举例：猜数字V1', '37133a6ec4c081278991cfe0233ca77ca0973d02b1780a5794e3836633bfda00', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完
2024/9/16
流程自动化
72
举例：猜数字V1') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10970, 43, 189, 182, 73, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
73
举例：猜数字V1

2024/9/16
流程自动化
73
举例：猜数字V1', 'bd7dee26477d02fab7f39fae0119c65316196b752c228b850b28fa58ab609e84', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
73
举例：猜数字V1') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10971, 43, 189, 182, 74, 0, '2024/9/16', '2024/9/16

2024/9/16
流程自动化
74
举例：猜数字V2

2024/9/16
流程自动化
74
举例：猜数字V2', '6ea3a7e3e3ccd25e15e4c68c3def3c40304e030eb540e3e55dc7a231a1f019ad', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第四章：流程自动化.pdf', '2024/9/16
流程自动化
74
举例：猜数字V2') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10972, 43, 190, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10973, 43, 190, NULL, 2, 0, '第五章：数据结构', '第五章：数据结构

第五章：数据结构

第五章：数据结构', '422d105407001520080310e9fa8d7d78fe2d91fbcbbdcdb1c86e5db02d8d2027', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '第五章：数据结构') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10974, 43, 190, NULL, 3, 0, '引言', '引言

引言
计算机不仅对单个变量表示的数据进行处理，更通常的情况是，计算机需要对一组数据进行批量处理。
例如：
给定一个学院的学生信息，统计男女生比例
搜索引擎推荐网页
一次实验产生了很多组数据，对这些大量数据进行分析
2024/9/2
数据结构
3

引言
计算机不仅对单个变量表示的数据进行处理，更通常的情况是，计算机需要对一组数据进行批量处理。
例如：
给定一个学院的学生信息，统计男女生比例
搜索引擎推荐网页
一次实验产生了很多组数据，对这些大量数据进行分析
2024/9/2
数据结构
3', '6e9b1eea84a57d458224dff2413499c653315ecc63375c803f17deda5e72c7f8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '引言
计算机不仅对单个变量表示的数据进行处理，更通常的情况是，计算机需要对一组数据进行批量处理。
例如：
给定一个学院的学生信息，统计男女生比例
搜索引擎推荐网页
一次实验产生了很多组数据，对这些大量数据进行分析
2024/9/2
数据结构
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10975, 43, 190, NULL, 4, 0, '数据结构', '数据结构

数据结构
实际计算中存在大量同时处理多个数据的情况，这需要将多个数据有效组织起来并统一表示，这种能够表示多个数据的类型称为组合数据类型（数据结构）。
操作更有序
操作更容易
2024/9/2
数据结构
4

数据结构
实际计算中存在大量同时处理多个数据的情况，这需要将多个数据有效组织起来并统一表示，这种能够表示多个数据的类型称为组合数据类型（数据结构）。
操作更有序
操作更容易
2024/9/2
数据结构
4', '978d5392962acd24cf89d18f1779e1fd16608bb23db50b479937faba4c8e71e8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '数据结构
实际计算中存在大量同时处理多个数据的情况，这需要将多个数据有效组织起来并统一表示，这种能够表示多个数据的类型称为组合数据类型（数据结构）。
操作更有序
操作更容易
2024/9/2
数据结构
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10976, 43, 190, NULL, 5, 0, '数据类型', '数据类型

数据类型
2024/9/2
数据结构
5

数据类型
2024/9/2
数据结构
5', '3dce117ce02cefb97020edcb65916469e2b9fb5d2e512fc509a9da8f53174a6f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '数据类型
2024/9/2
数据结构
5') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10977, 43, 190, NULL, 6, 0, '目录', '目录

目录
5.1、序列
5.2、集合
5.3、字典
2024/9/2
数据结构
6

目录
5.1、序列
5.2、集合
5.3、字典
2024/9/2
数据结构
6', '7b0bdb8e34c897f808a32b59c3bed07f8a17a87e3b41624c2bc6382d9d263403', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '目录
5.1、序列
5.2、集合
5.3、字典
2024/9/2
数据结构
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10978, 43, 190, 183, 7, 0, '5.1、序列', '5.1、序列

5.1、序列
2024/9/2
数据结构
7

5.1、序列
2024/9/2
数据结构
7', '2fa11f936afea7e403dc528b9022ae55ac36d86da4f7155e8d8756170862577d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '5.1、序列
2024/9/2
数据结构
7') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10979, 43, 190, 183, 8, 0, '序列', '序列

序列
序列：有序的元素排列
序列类型是一维元素向量，元素之间存在先后关系，通过序号访问。
当需要访问序列中某特定值时，只需要通过下标标出即可。
2024/9/2
数据结构
8

序列
序列：有序的元素排列
序列类型是一维元素向量，元素之间存在先后关系，通过序号访问。
当需要访问序列中某特定值时，只需要通过下标标出即可。
2024/9/2
数据结构
8', '98814d4c168ede73a9f78a671919956e5940ce74fe083243696fee73af813a94', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '序列
序列：有序的元素排列
序列类型是一维元素向量，元素之间存在先后关系，通过序号访问。
当需要访问序列中某特定值时，只需要通过下标标出即可。
2024/9/2
数据结构
8') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10980, 43, 190, 183, 9, 0, '序列类型', '序列类型

序列类型
元组是包含0个或多个数据项的不可变序列类型。元组生成后是固定的，其中任何数据项不能替换或删除。
列表则是一个可以修改数据项的序列类型
2024/9/2
数据结构
9

序列类型
元组是包含0个或多个数据项的不可变序列类型。元组生成后是固定的，其中任何数据项不能替换或删除。
列表则是一个可以修改数据项的序列类型
2024/9/2
数据结构
9', '39495d11f0318a0f0cc313a012791c17964a2d62a58c72a5cf1fe19e039f14b9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '序列类型
元组是包含0个或多个数据项的不可变序列类型。元组生成后是固定的，其中任何数据项不能替换或删除。
列表则是一个可以修改数据项的序列类型
2024/9/2
数据结构
9') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10981, 43, 190, 183, 10, 0, '序列的定义', '序列的定义

序列的定义
字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
2024/9/2
数据结构
10

序列的定义
字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
2024/9/2
数据结构
10', 'a1b5324d6806f70fd77d03b112a125f296f61bcd1b91f30e9040a14ff4cc1c5c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '序列的定义
字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
2024/9/2
数据结构
10') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10982, 43, 190, 183, 11, 0, '1索引', '1索引

1索引
2.切片
3.序列相加
4.序列乘法
5.成员资格
6.长度
7.最大值
8.最小值
9.计数
10.for循环
序列的基本操作
2024/9/2
数据结构
11

1索引
2.切片
3.序列相加
4.序列乘法
5.成员资格
6.长度
7.最大值
8.最小值
9.计数
10.for循环
序列的基本操作
2024/9/2
数据结构
11', 'a588d750bd2781392cf6a69063d3a7d721a5974fbfea5036ac48d858e2e67cb5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '1索引
2.切片
3.序列相加
4.序列乘法
5.成员资格
6.长度
7.最大值
8.最小值
9.计数
10.for循环
序列的基本操作
2024/9/2
数据结构
11') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10983, 43, 190, 183, 12, 0, '1.序列的索引操作', '1.序列的索引操作

1.序列的索引操作
字符串


元组


列表
2024/9/2
数据结构
12

1.序列的索引操作
字符串


元组


列表
2024/9/2
数据结构
12', '3b8e1f62cc3b6605c44d0e1eadc026bcbef2d1177148129973e5b9a6047390ca', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '1.序列的索引操作
字符串


元组


列表
2024/9/2
数据结构
12') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10984, 43, 190, 183, 13, 0, '注意：避免索引错误', '注意：避免索引错误

注意：避免索引错误
2024/9/2
数据结构
13

注意：避免索引错误
2024/9/2
数据结构
13', '4a979201750b30f2ffccd39d6ad01bd63d97529a52c6aef46f6e6bebdf835c1b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '注意：避免索引错误
2024/9/2
数据结构
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10985, 43, 190, 183, 14, 0, '2.序列的切片操作', '2.序列的切片操作

2.序列的切片操作
字符串


元组


列表
2024/9/2
数据结构
14

2.序列的切片操作
字符串


元组


列表
2024/9/2
数据结构
14', '981533d1a662bf23fb89c4a4b4c96f0e71863830aa16ffec1ff9c126e48da17e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '2.序列的切片操作
字符串


元组


列表
2024/9/2
数据结构
14') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10986, 43, 190, 183, 15, 0, '3.序列相加', '3.序列相加

3.序列相加
字符串


元组


列表
2024/9/2
数据结构
15

3.序列相加
字符串


元组


列表
2024/9/2
数据结构
15', 'a63662ee2e6e8dbf65d00572fc02adf4c46a20bf704bafaacf463a12e492116f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '3.序列相加
字符串


元组


列表
2024/9/2
数据结构
15') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10987, 43, 190, 183, 16, 0, '4.序列乘法', '4.序列乘法

4.序列乘法
字符串


元组


列表
2024/9/2
数据结构
16

4.序列乘法
字符串


元组


列表
2024/9/2
数据结构
16', '1242e660cff815d22741c189e4763e89f294fa25f03db4b1f0097f977c54ba4d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '4.序列乘法
字符串


元组


列表
2024/9/2
数据结构
16') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10988, 43, 190, 183, 17, 0, '5.成员资格', '5.成员资格

5.成员资格
字符串


元组


列表
2024/9/2
数据结构
17

5.成员资格
字符串


元组


列表
2024/9/2
数据结构
17', '797583edb69d875b18e6a1c9e7261cc2f4c95806018ec2e77fa7bca310c5f1bc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '5.成员资格
字符串


元组


列表
2024/9/2
数据结构
17') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10989, 43, 190, 183, 18, 0, '6.序列长度', '6.序列长度

6.序列长度
字符串


元组


列表
2024/9/2
数据结构
18

6.序列长度
字符串


元组


列表
2024/9/2
数据结构
18', '22f78b550d8e7de557e10bd662d1ad0375efb6e723a651aa23373dbe14fc520e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '6.序列长度
字符串


元组


列表
2024/9/2
数据结构
18') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10990, 43, 190, 183, 19, 0, '7.序列最大值', '7.序列最大值

7.序列最大值
字符串


元组


列表
2024/9/2
数据结构
19

7.序列最大值
字符串


元组


列表
2024/9/2
数据结构
19', '67aca47fefa8e0f06b86e2f8c984fd018d374372f6d201af234c4bc5216255e2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '7.序列最大值
字符串


元组


列表
2024/9/2
数据结构
19') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10991, 43, 190, 183, 20, 0, '8.序列最小值', '8.序列最小值

8.序列最小值
字符串


元组


列表
2024/9/2
数据结构
20

8.序列最小值
字符串


元组


列表
2024/9/2
数据结构
20', '6e1f16147b1bbae502d866011dc41b2d285f3478cf4ae50d0886a18cb137806f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '8.序列最小值
字符串


元组


列表
2024/9/2
数据结构
20') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10992, 43, 190, 183, 21, 0, '9.序列计数', '9.序列计数

9.序列计数
字符串


元组


列表
2024/9/2
数据结构
21

9.序列计数
字符串


元组


列表
2024/9/2
数据结构
21', '2f1a995e5ae96df770ff64492c648dd2a33ed8d4267df741f76b852ce5d7b3fc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '9.序列计数
字符串


元组


列表
2024/9/2
数据结构
21') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10993, 43, 190, 183, 22, 0, '10.for循环', '10.for循环

10.for循环
for item in ls:
    <语句块>
2024/9/2
数据结构
22

10.for循环
for item in ls:
    <语句块>
2024/9/2
数据结构
22', '17509f3089f0e2c2884b25719055b949bc89e4fc66a3c29d4a18758edbcee579', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '10.for循环
for item in ls:
    <语句块>
2024/9/2
数据结构
22') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10994, 43, 190, 183, 23, 0, '列表的额外操作', '列表的额外操作

列表的额外操作
元组是不可修改的
列表是可以修改的
额外操作包括：
修改
增加
删除
排序
2024/9/2
数据结构
23

列表的额外操作
元组是不可修改的
列表是可以修改的
额外操作包括：
修改
增加
删除
排序
2024/9/2
数据结构
23', '10cfc50bd17396f33e742ca43cf3729b50b80708ddb15fd9970b73def09c8baf', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '列表的额外操作
元组是不可修改的
列表是可以修改的
额外操作包括：
修改
增加
删除
排序
2024/9/2
数据结构
23') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10995, 43, 190, 183, 24, 0, '修改列表元素', '修改列表元素

修改列表元素
ls[i] = x #替换列表ls的第i个元素为x
2024/9/2
数据结构
24

修改列表元素
ls[i] = x #替换列表ls的第i个元素为x
2024/9/2
数据结构
24', 'ac8cd017a8aeddd08d466ccdbe2ad959ec46a3c97bd5059bdb22f6ebdd1a2ffa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '修改列表元素
ls[i] = x #替换列表ls的第i个元素为x
2024/9/2
数据结构
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10996, 43, 190, 183, 25, 0, '在列表中添加元素', '在列表中添加元素

在列表中添加元素
在列表末尾添加元素
ls.append(x)
2024/9/2
数据结构
25

在列表中添加元素
在列表末尾添加元素
ls.append(x)
2024/9/2
数据结构
25', '5c0177619f4bca5e81b68859fe5c65fafba3529f6eea74da6b7dc9780b7c90bb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '在列表中添加元素
在列表末尾添加元素
ls.append(x)
2024/9/2
数据结构
25') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10997, 43, 190, 183, 26, 0, '在列表中添加元素(2)', '在列表中添加元素(2)

在列表中添加元素(2)
在列表中插入元素
ls.insert(i,x)
2024/9/2
数据结构
26

在列表中添加元素(2)
在列表中插入元素
ls.insert(i,x)
2024/9/2
数据结构
26', 'a65d3b448cf5612a59789f0095f819016587e7dfaf8bf646d05bf210623622c5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '在列表中添加元素(2)
在列表中插入元素
ls.insert(i,x)
2024/9/2
数据结构
26') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10998, 43, 190, 183, 27, 0, '从列表中删除元素', '从列表中删除元素

从列表中删除元素
使用del语句删除元素
del ls[i]
del ls[i:j]
2024/9/2
数据结构
27

从列表中删除元素
使用del语句删除元素
del ls[i]
del ls[i:j]
2024/9/2
数据结构
27', '6f001b5d0f577ef3f9603e1af427ac4753b438af4998190e2890dc0d6e90497b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '从列表中删除元素
使用del语句删除元素
del ls[i]
del ls[i:j]
2024/9/2
数据结构
27') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (10999, 43, 190, 183, 28, 0, '从列表中删除元素(2)', '从列表中删除元素(2)

从列表中删除元素(2)
使用方法pop()删除元素，并获得该元素
ls.pop()
ls.pop(i)
2024/9/2
数据结构
28

从列表中删除元素(2)
使用方法pop()删除元素，并获得该元素
ls.pop()
ls.pop(i)
2024/9/2
数据结构
28', '334d6a7bf88d8f7e3ca72786536e3dd39577c8d48b6d106f3555ed40f40313a7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '从列表中删除元素(2)
使用方法pop()删除元素，并获得该元素
ls.pop()
ls.pop(i)
2024/9/2
数据结构
28') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11000, 43, 190, 183, 29, 0, '从列表中删除元素(3)', '从列表中删除元素(3)

从列表中删除元素(3)
根据元素值来删除元素，删除列表中出现的第一个元素x
ls.remove(x)
2024/9/2
数据结构
29

从列表中删除元素(3)
根据元素值来删除元素，删除列表中出现的第一个元素x
ls.remove(x)
2024/9/2
数据结构
29', 'ed9fe18ebd3c6ee465db1f7006da427c73857b49c22275eaef1d37da82f359b5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '从列表中删除元素(3)
根据元素值来删除元素，删除列表中出现的第一个元素x
ls.remove(x)
2024/9/2
数据结构
29') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11001, 43, 190, 183, 30, 0, '列表排序', '列表排序

列表排序
使用方法sort()对列表进行永久性排序
ls.sort()
ls.sort(reverse = True)
2024/9/2
数据结构
30

列表排序
使用方法sort()对列表进行永久性排序
ls.sort()
ls.sort(reverse = True)
2024/9/2
数据结构
30', '8932152359b9541a1d80181852f70ddcd03bcd381719bd6ca64c1f295c2b1db3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '列表排序
使用方法sort()对列表进行永久性排序
ls.sort()
ls.sort(reverse = True)
2024/9/2
数据结构
30') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11002, 43, 190, 183, 31, 0, '列表排序', '列表排序

列表排序
使用函数sorted()对列表进行临时排序
sorted(ls)
2024/9/2
数据结构
31

列表排序
使用函数sorted()对列表进行临时排序
sorted(ls)
2024/9/2
数据结构
31', '2e7ecc6108890131651576c8c55d8715f9157ca529fb709f7f80b7b7a7ced1b9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '列表排序
使用函数sorted()对列表进行临时排序
sorted(ls)
2024/9/2
数据结构
31') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11003, 43, 190, 183, 32, 0, '反转/倒排列表', '反转/倒排列表

反转/倒排列表
使用reverse()函数倒排列表元素的排列顺序
2024/9/2
数据结构
32

反转/倒排列表
使用reverse()函数倒排列表元素的排列顺序
2024/9/2
数据结构
32', 'c201e3d8eaf02892707387caf02d8440b457a24dfea2bb822a97c4bcfd4e7955', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '反转/倒排列表
使用reverse()函数倒排列表元素的排列顺序
2024/9/2
数据结构
32') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11004, 43, 190, 183, 33, 0, '字符串和列表相互转换', '字符串和列表相互转换

字符串和列表相互转换
2024/9/2
数据结构
33

字符串和列表相互转换
2024/9/2
数据结构
33', '6f9942a6ce1cb4cc49f3fba9e9f206b32a71fe3a237f82310a68e8cf886a2298', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字符串和列表相互转换
2024/9/2
数据结构
33') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11005, 43, 190, 183, 34, 0, '一次input()获得多个值', '一次input()获得多个值

一次input()获得多个值
2024/9/2
数据结构
34

一次input()获得多个值
2024/9/2
数据结构
34', '87f83093e36c4b4016b5cacbaf2cc296cc2ff9ccaf7f2503b282fcb8924a7ff8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '一次input()获得多个值
2024/9/2
数据结构
34') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11006, 43, 190, 183, 35, 0, '组合数据类型合并输出', '组合数据类型合并输出

组合数据类型合并输出
2024/9/2
数据结构
35

组合数据类型合并输出
2024/9/2
数据结构
35', '220dfdc759d7f4897c8f876cb1d48acb91f3632650634d648ef64bb68c6b37e6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '组合数据类型合并输出
2024/9/2
数据结构
35') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11007, 43, 190, 183, 36, 0, 'Random+列表', 'Random+列表

Random+列表
2024/9/2
数据结构
36

Random+列表
2024/9/2
数据结构
36', 'f71df383dbba57358ebc4b839a4e088a62d04ed1576d136d442551b409bdb08b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', 'Random+列表
2024/9/2
数据结构
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11008, 43, 190, 183, 37, 0, 'Random + choice', 'Random + choice

Random + choice
2024/9/2
数据结构
37

Random + choice
2024/9/2
数据结构
37', '419443e3b39a5e06f54d48983426aa8e1e7e6bf3f1fb4cd5acaedb34aa3a0f99', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', 'Random + choice
2024/9/2
数据结构
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11009, 43, 190, 183, 38, 0, 'Random + shuffle', 'Random + shuffle

Random + shuffle
2024/9/2
数据结构
38

Random + shuffle
2024/9/2
数据结构
38', '4ef85fb9d3bafe44a72cb914d860e2a4fb0f572154bb14baf242606c7d4b41f2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', 'Random + shuffle
2024/9/2
数据结构
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11010, 43, 190, 183, 39, 0, 'Random+sample', 'Random+sample

Random+sample
2024/9/2
数据结构
39

Random+sample
2024/9/2
数据结构
39', 'ef583c52b395f0dba3e615b9e1996e0f04f0e96ea8f8b9caa0dd0ba1a0643d08', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', 'Random+sample
2024/9/2
数据结构
39') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11011, 43, 190, 183, 40, 0, '举例：鱿鱼游戏之击鼓传花', '举例：鱿鱼游戏之击鼓传花

举例：鱿鱼游戏之击鼓传花
击鼓传花，也称传彩球。中国民间游戏，流行于中国各地。数人、十数人或数十人围成一个圆圈席地而坐，另外一个人背对着人圈以槌击鼓。鼓响时，开始传花，花由一个人的手里传。——百度百科
2024/9/2
数据结构
40

举例：鱿鱼游戏之击鼓传花
击鼓传花，也称传彩球。中国民间游戏，流行于中国各地。数人、十数人或数十人围成一个圆圈席地而坐，另外一个人背对着人圈以槌击鼓。鼓响时，开始传花，花由一个人的手里传。——百度百科
2024/9/2
数据结构
40', 'ea39cad03acbb31fdc40f605fc585a30a6da44cad0058cc56bb7724140385ec3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：鱿鱼游戏之击鼓传花
击鼓传花，也称传彩球。中国民间游戏，流行于中国各地。数人、十数人或数十人围成一个圆圈席地而坐，另外一个人背对着人圈以槌击鼓。鼓响时，开始传花，花由一个人的手里传。——百度百科
2024/9/2
数据结构
40') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11012, 43, 190, NULL, 41, 0, '2024/9/2', '2024/9/2

2024/9/2
数据结构
41

2024/9/2
数据结构
41', '8e6ce3146f8f19787c357d6589075861c8b71bb160935835b62ad578a55e4498', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '2024/9/2
数据结构
41') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11013, 43, 190, 183, 42, 0, '集合类型', '集合类型

集合类型
集合类型与数学中集合的概念一致，即包含0个或多个数据项的无序组合
集合中元素不可重复
2024/9/2
数据结构
42

集合类型
集合类型与数学中集合的概念一致，即包含0个或多个数据项的无序组合
集合中元素不可重复
2024/9/2
数据结构
42', '0027a322a58cff805a72f890a0785d24053ae1aece1c4a31aade9a251e6b9eed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '集合类型
集合类型与数学中集合的概念一致，即包含0个或多个数据项的无序组合
集合中元素不可重复
2024/9/2
数据结构
42') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11014, 43, 190, 183, 43, 0, '序列类型', '序列类型

序列类型
元素允许重复
有序
索引操作
切片操作
集合类型
元素不允许重复
无序
不能进行索引操作
不能进行切片操作
2024/9/2
数据结构
43
序列和集合对比

序列类型
元素允许重复
有序
索引操作
切片操作
集合类型
元素不允许重复
无序
不能进行索引操作
不能进行切片操作
2024/9/2
数据结构
43
序列和集合对比', 'd9d2d55caaabc195c9f36cf531ddd0932aaa0d28b6f482ed222f829f2f58dbf6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '序列类型
元素允许重复
有序
索引操作
切片操作
集合类型
元素不允许重复
无序
不能进行索引操作
不能进行切片操作
2024/9/2
数据结构
43
序列和集合对比') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11015, 43, 190, 183, 44, 0, '5.2、集合', '5.2、集合

5.2、集合
2024/9/2
数据结构
44

5.2、集合
2024/9/2
数据结构
44', '579c6032cdfebf8ebfa2b2e16b1254061ccb1c4e1f4d4eb808c0c77f843f7f12', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '5.2、集合
2024/9/2
数据结构
44') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11016, 43, 190, 183, 45, 0, '字符串', '字符串

字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
集合
ddd_set = {1,2,3}
ddd_set1 = {1,2,3,3}
ddd_set = {2,1,3}
2024/9/2
数据结构
45
集合的定义

字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
集合
ddd_set = {1,2,3}
ddd_set1 = {1,2,3,3}
ddd_set = {2,1,3}
2024/9/2
数据结构
45
集合的定义', '6a3299299ea6136aec8ff09e059fb5eb7030eef187565cc3e264e3039164bd8d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字符串
aaa_str = “hello world”
元组
bbb_tuple = (1,2,3,4)
列表
ccc_list = [1,2,3,4]
集合
ddd_set = {1,2,3}
ddd_set1 = {1,2,3,3}
ddd_set = {2,1,3}
2024/9/2
数据结构
45
集合的定义') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11017, 43, 190, 183, 46, 0, '集合的打印效果与定义顺序可以不一致', '集合的打印效果与定义顺序可以不一致

集合的打印效果与定义顺序可以不一致
由于集合元素独一无二，可以使用集合类型来过滤重复的元素。
2024/9/2
数据结构
46
集合的定义

集合的打印效果与定义顺序可以不一致
由于集合元素独一无二，可以使用集合类型来过滤重复的元素。
2024/9/2
数据结构
46
集合的定义', '9433bccb1f18058b298c9e79ba7435c94104e8dba497c310f94a43ec18e5f959', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '集合的打印效果与定义顺序可以不一致
由于集合元素独一无二，可以使用集合类型来过滤重复的元素。
2024/9/2
数据结构
46
集合的定义') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11018, 43, 190, 183, 47, 0, '集合类型的操作符', '集合类型的操作符

集合类型的操作符
集合类型的4种基本操作，并集（|）、差集（-）、交集（&）、补集（^），操作逻辑与数学定义相同
2024/9/2
数据结构
47

集合类型的操作符
集合类型的4种基本操作，并集（|）、差集（-）、交集（&）、补集（^），操作逻辑与数学定义相同
2024/9/2
数据结构
47', 'a597eee16ec9a88197029420e481854f459213d57b01c43983b89bbd9f61cba7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '集合类型的操作符
集合类型的4种基本操作，并集（|）、差集（-）、交集（&）、补集（^），操作逻辑与数学定义相同
2024/9/2
数据结构
47') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11019, 43, 190, 183, 48, 0, '并集', '并集

并集
S|T 或 S.union(T)
Union函数有返回值
2024/9/2
数据结构
48
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
love_place_set3 = love_place_set1.union(love_place_set2)
print("被人喜欢的地点有:{}".format(love_place_set3))
love_place_set4 = love_place_set1 | love_place_set2
print("或者，被人喜欢的地点有:{}".format(love_place_set4))

并集
S|T 或 S.union(T)
Union函数有返回值
2024/9/2
数据结构
48
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_se…', '89b08e952660288b24577aecb411a5707945d86c6568ee7f43d0a5c61c4ed2a7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '并集
S|T 或 S.union(T)
Union函数有返回值
2024/9/2
数据结构
48
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_se…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11020, 43, 190, 183, 49, 0, '差集', '差集

差集
S-T 或 S.difference(T)
difference函数有返回值
2024/9/2
数据结构
49
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
onlylove_place_set1 = love_place_set1.difference(love_place_set2)
print("第一个人的唯粉地点是:{}".format(onlylove_place_set1))
onlylove_place_set2 = love_place_set2 - love_place_set1
print("第二个人的唯粉地点是:{}".format(onlylove_place_set2))

差集
S-T 或 S.difference(T)
difference函数有返回值
2024/9/2
数据结构
49
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(lov…', '9271a87ca3b1d15ccaf68b0a52735e77b802b6ffdf13cbe02c08d8e11daa8a23', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '差集
S-T 或 S.difference(T)
difference函数有返回值
2024/9/2
数据结构
49
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(lov…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11021, 43, 190, 183, 50, 0, '交集', '交集

交集
S & T 或 S.intersection(T)
函数有返回值
2024/9/2
数据结构
50
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
common_place_set = love_place_set1.intersection(love_place_set2)
print("两个人都喜欢的地点是:{}".format(common_place_set))

交集
S & T 或 S.intersection(T)
函数有返回值
2024/9/2
数据结构
50
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_plac…', '4cbd1f5f70fd8a9f33189f91ca978fdc710890e00a93d4710d98aa6b04c35c99', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '交集
S & T 或 S.intersection(T)
函数有返回值
2024/9/2
数据结构
50
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_plac…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11022, 43, 190, 183, 51, 0, '补集', '补集

补集
S ^ T 或 S.symmetric_difference(T)
函数有返回值
2024/9/2
数据结构
51
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
onelove_place_set = love_place_set1.symmetric_difference(love_place_set2)
print("只有一个女孩喜欢的地点是:{}".format(onelove_place_set))

补集
S ^ T 或 S.symmetric_difference(T)
函数有返回值
2024/9/2
数据结构
51
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(l…', '55812e22276be7e1bde6839f9d2b334c49a9ace15b3eb9c277922e7867c7cb1d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '补集
S ^ T 或 S.symmetric_difference(T)
函数有返回值
2024/9/2
数据结构
51
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(l…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11023, 43, 190, 183, 52, 0, '集合类型的复合赋值操作符', '集合类型的复合赋值操作符

集合类型的复合赋值操作符
2024/9/2
数据结构
52
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
love_place_set1.symmetric_difference_update(love_place_set2)
print("只有一个女孩喜欢的地点是:{}".format(love_place_set1))

集合类型的复合赋值操作符
2024/9/2
数据结构
52
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
love_place_se…', 'a959890e8a21b361d23609fe79fe57e1111d6ac4a7a70addebd46c135d3865f3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '集合类型的复合赋值操作符
2024/9/2
数据结构
52
love_place_set1 = {"天水","淄博"}
love_place_set2 = {"哈尔滨","天水"}
print("第一个人喜欢的地点是:{}".format(love_place_set1))
print("第二个人喜欢的地点是:{}".format(love_place_set1))
love_place_se…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11024, 43, 190, 183, 53, 0, '长度', '长度

长度
len()用来返回集合的长度，即集合包含的元素的个数
2024/9/2
数据结构
53

长度
len()用来返回集合的长度，即集合包含的元素的个数
2024/9/2
数据结构
53', '84b22d264a01c11c39c11c93cc16f9918917c267e0c90b887c65081022785396', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '长度
len()用来返回集合的长度，即集合包含的元素的个数
2024/9/2
数据结构
53') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11025, 43, 190, 183, 54, 0, '集合基本操作函数', '集合基本操作函数

集合基本操作函数
集合判断
成员资格
增加元素
删除元素
2024/9/2
数据结构
54

集合基本操作函数
集合判断
成员资格
增加元素
删除元素
2024/9/2
数据结构
54', 'ac2eae055cac73d3cfc83746f06df7c5e3074610a6d38d16c28d612b1c335b7a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '集合基本操作函数
集合判断
成员资格
增加元素
删除元素
2024/9/2
数据结构
54') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11026, 43, 190, 183, 55, 0, '子集判断', '子集判断

子集判断
判断S与T相同或者S是T的子集
S <= T 或 S.issubset(T)
2024/9/2
数据结构
55

子集判断
判断S与T相同或者S是T的子集
S <= T 或 S.issubset(T)
2024/9/2
数据结构
55', '7a5c0a75dfaf9925c5c762c6ed2f130aba16e87b42c7bdc004805eda2ce986da', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '子集判断
判断S与T相同或者S是T的子集
S <= T 或 S.issubset(T)
2024/9/2
数据结构
55') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11027, 43, 190, 183, 56, 0, '超集判断', '超集判断

超集判断
判断S与T相同或者S是T的超集
S >= T 或 S.issuperset(T)
2024/9/2
数据结构
56

超集判断
判断S与T相同或者S是T的超集
S >= T 或 S.issuperset(T)
2024/9/2
数据结构
56', '1ccffdb982aa8bfc7c6ed0dac90a21f2a59ddb85a43902564fa6f5a93cd9947c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '超集判断
判断S与T相同或者S是T的超集
S >= T 或 S.issuperset(T)
2024/9/2
数据结构
56') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11028, 43, 190, 183, 57, 0, '互斥判断', '互斥判断

互斥判断
判断集合S和T中有没有相同元素
S.isdisjoint(T)
2024/9/2
数据结构
57

互斥判断
判断集合S和T中有没有相同元素
S.isdisjoint(T)
2024/9/2
数据结构
57', 'c98f7ef9eac700ade96a9367d53cff7f64026f901cdfb82a74bb54fb0d6d6eea', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '互斥判断
判断集合S和T中有没有相同元素
S.isdisjoint(T)
2024/9/2
数据结构
57') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11029, 43, 190, 183, 58, 0, '成员资格', '成员资格

成员资格
x in S
x not in S
2024/9/2
数据结构
58

成员资格
x in S
x not in S
2024/9/2
数据结构
58', 'eb598ce257be0977f5f5e68a523e548ddbad4aae37cfe4400fe47c03c77aff1f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '成员资格
x in S
x not in S
2024/9/2
数据结构
58') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11030, 43, 190, 183, 59, 0, '增加元素', '增加元素

增加元素
S.add(x)
如果元素x不在集合S中，将x增加到S
如果元素x在集合S中，不增加
2024/9/2
数据结构
59

增加元素
S.add(x)
如果元素x不在集合S中，将x增加到S
如果元素x在集合S中，不增加
2024/9/2
数据结构
59', '696cfc5131b58ab160248829d4ef188f7c04315cff4a6cf290f3148ae6e217fc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '增加元素
S.add(x)
如果元素x不在集合S中，将x增加到S
如果元素x在集合S中，不增加
2024/9/2
数据结构
59') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11031, 43, 190, 183, 60, 0, '增加元素(2)', '增加元素(2)

增加元素(2)
S.update(a_set)
使用集合a_set更新原集合，即把a_set中的元素放入原集合中
相当于用S与a_set的并集来更新S
2024/9/2
数据结构
60

增加元素(2)
S.update(a_set)
使用集合a_set更新原集合，即把a_set中的元素放入原集合中
相当于用S与a_set的并集来更新S
2024/9/2
数据结构
60', '498bab1a70f5b66521906ac03a5c3a98b2fa37eb17acfbc924b4b1ade2222cd5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '增加元素(2)
S.update(a_set)
使用集合a_set更新原集合，即把a_set中的元素放入原集合中
相当于用S与a_set的并集来更新S
2024/9/2
数据结构
60') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11032, 43, 190, 183, 61, 0, '删除操作', '删除操作

删除操作
S.pop()
随机返回集合S中的一个元素
如果S为空，产生KeyError异常
2024/9/2
数据结构
61

删除操作
S.pop()
随机返回集合S中的一个元素
如果S为空，产生KeyError异常
2024/9/2
数据结构
61', '21574f605fe351a80956a0455fa66aa6a36ccd7a31d1d9dbff98d7caeb0cd44f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '删除操作
S.pop()
随机返回集合S中的一个元素
如果S为空，产生KeyError异常
2024/9/2
数据结构
61') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11033, 43, 190, 183, 62, 0, 'S.remove(x)', 'S.remove(x)

S.remove(x)
如果x在集合S中，移除该元素
如果x不在集合S中，产生KeyError异常
S.discard(x)
如果x在集合S中，移除该元素
如果x不在集合S中，不报错
2024/9/2
数据结构
62
删除操作(2)

S.remove(x)
如果x在集合S中，移除该元素
如果x不在集合S中，产生KeyError异常
S.discard(x)
如果x在集合S中，移除该元素
如果x不在集合S中，不报错
2024/9/2
数据结构
62
删除操作(2)', '837767438e6d25b97c5991fba55bdbbd4da759d595f2d71c2e2ed53ab0842678', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', 'S.remove(x)
如果x在集合S中，移除该元素
如果x不在集合S中，产生KeyError异常
S.discard(x)
如果x在集合S中，移除该元素
如果x不在集合S中，不报错
2024/9/2
数据结构
62
删除操作(2)') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11034, 43, 190, 183, 63, 0, '清空集合中的所有元素', '清空集合中的所有元素

清空集合中的所有元素
S.clear()
移除S中的所有数据项
2024/9/2
数据结构
63

清空集合中的所有元素
S.clear()
移除S中的所有数据项
2024/9/2
数据结构
63', '1fe8ebb95aed0c06f839bc74fa3b23e798e2c14db4234f84ed5b8f06088936b1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '清空集合中的所有元素
S.clear()
移除S中的所有数据项
2024/9/2
数据结构
63') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11035, 43, 190, 183, 64, 0, '举例：统计需要取快递人员的名单', '举例：统计需要取快递人员的名单

举例：统计需要取快递人员的名单
大学城每天都能收到很多快递。请为快递员写一个程序统计一下收到快递的人员名单，以便统一通知。
2024/9/2
数据结构
64

举例：统计需要取快递人员的名单
大学城每天都能收到很多快递。请为快递员写一个程序统计一下收到快递的人员名单，以便统一通知。
2024/9/2
数据结构
64', '52ed95134e887c3db45729507a05fe3ef1899e79ff70718be67f8fdde1256264', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：统计需要取快递人员的名单
大学城每天都能收到很多快递。请为快递员写一个程序统计一下收到快递的人员名单，以便统一通知。
2024/9/2
数据结构
64') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11036, 43, 190, 183, 65, 0, '统计需要取快递人员的名单', '统计需要取快递人员的名单

统计需要取快递人员的名单
2024/9/2
数据结构
65
#收货人员名单统计
contact_set = set()

while True:
    name = input("请输入收货人姓名，输入EXIT时退出:")

    if name == "EXIT":
        print("结束录入！")
        print("所有收货人名单如下：")
        for contact in contact_set:
            print(contact)
        break

    else:
        contact_set.add(name)
        print("收货人{}已录入!!".format(name))

统计需要取快递人员的名单
2024/9/2
数据结构
65
#收货人员名单统计
contact_set = set()

while True:
    name = input("请输入收货人姓名，输入EXIT时退出:")

    if name == "EXIT":
        print("结束录入！")
        print("所有收货人名单如下：")
        for …', '909865b9a4c6949c850f1bfd1beaad7e6e0e060ae19333e5d76757665679d2ed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '统计需要取快递人员的名单
2024/9/2
数据结构
65
#收货人员名单统计
contact_set = set()

while True:
    name = input("请输入收货人姓名，输入EXIT时退出:")

    if name == "EXIT":
        print("结束录入！")
        print("所有收货人名单如下：")
        for …') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11037, 43, 190, 183, 66, 0, '5.3、字典', '5.3、字典

5.3、字典
2024/9/2
数据结构
66

5.3、字典
2024/9/2
数据结构
66', 'd91b7fe96f7ae632321d68ccd26d6597dc0658aff84a03333a445ba1061239d7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '5.3、字典
2024/9/2
数据结构
66') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11038, 43, 190, 183, 67, 0, '字典类型', '字典类型

字典类型
之前的组合数据结构往往只能存储一种类型的数据
人物列表 / 选项列表 / 字符串列表 / 数字列表
在有些情况下，需要同时存储多种类型的数据
学生名单
成绩单
2024/9/2
数据结构
67
学号为18110004的同学叫什么名字？

字典类型
之前的组合数据结构往往只能存储一种类型的数据
人物列表 / 选项列表 / 字符串列表 / 数字列表
在有些情况下，需要同时存储多种类型的数据
学生名单
成绩单
2024/9/2
数据结构
67
学号为18110004的同学叫什么名字？', 'e749c9cd9b538edfb269df3ba312e3c7362ac329bd268cd4acae3aeb8e7cbe15', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字典类型
之前的组合数据结构往往只能存储一种类型的数据
人物列表 / 选项列表 / 字符串列表 / 数字列表
在有些情况下，需要同时存储多种类型的数据
学生名单
成绩单
2024/9/2
数据结构
67
学号为18110004的同学叫什么名字？') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11039, 43, 190, 183, 68, 0, '单向映射', '单向映射

单向映射
Apple  苹果
JJ  林俊杰
181100001  彭一，软件1801
二维码  商品名称，商品金额，…
键
值

将两类（或多类）不同的数据进行关联
这种关联是单向的
2024/9/2
数据结构
68

单向映射
Apple  苹果
JJ  林俊杰
181100001  彭一，软件1801
二维码  商品名称，商品金额，…
键
值

将两类（或多类）不同的数据进行关联
这种关联是单向的
2024/9/2
数据结构
68', 'f253c621539b664a43a688be158b77839a371b858dd543a5ea0d9cf3fa940f07', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '单向映射
Apple  苹果
JJ  林俊杰
181100001  彭一，软件1801
二维码  商品名称，商品金额，…
键
值

将两类（或多类）不同的数据进行关联
这种关联是单向的
2024/9/2
数据结构
68') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11040, 43, 190, 183, 69, 0, '单向映射', '单向映射

单向映射
键
值

这种关联是单向的
键必须是唯一的，不允许同一个键出现两次
值可以重复
2024/9/2
数据结构
69

单向映射
键
值

这种关联是单向的
键必须是唯一的，不允许同一个键出现两次
值可以重复
2024/9/2
数据结构
69', '766edff1d535a69963f7de8e1f56b6caf5bf84b37909c58e270b721c5cc932e0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '单向映射
键
值

这种关联是单向的
键必须是唯一的，不允许同一个键出现两次
值可以重复
2024/9/2
数据结构
69') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11041, 43, 190, 183, 70, 0, '字典类型', '字典类型

字典类型
字典类型是“键-值”数据项的组合，每个元素是一个键值对，即元素是(key, value)，元素之间是无序的。
键值对(key, value)是一种二元关系
2024/9/2
数据结构
70

字典类型
字典类型是“键-值”数据项的组合，每个元素是一个键值对，即元素是(key, value)，元素之间是无序的。
键值对(key, value)是一种二元关系
2024/9/2
数据结构
70', '2679c5c7c431adf1f293e6c1706c653480990edd3126cecf0fa731f701ac6e18', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字典类型
字典类型是“键-值”数据项的组合，每个元素是一个键值对，即元素是(key, value)，元素之间是无序的。
键值对(key, value)是一种二元关系
2024/9/2
数据结构
70') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11042, 43, 190, 183, 71, 0, '字典类型的基本概念', '字典类型的基本概念

字典类型的基本概念
Python语言中通过字典实现映射
Python语言中的字典可以通过大括号{}建立，建立模式如下：
{<键1>:<值1>, <键2>:<值2>, … , <键n>:<值n>}
键和值通过冒号连接
不同键值对通过逗号隔开
2024/9/2
数据结构
71

字典类型的基本概念
Python语言中通过字典实现映射
Python语言中的字典可以通过大括号{}建立，建立模式如下：
{<键1>:<值1>, <键2>:<值2>, … , <键n>:<值n>}
键和值通过冒号连接
不同键值对通过逗号隔开
2024/9/2
数据结构
71', '7b7ea00ee32d2d46fe16cc63cb858cc82968b769dac5dbd1d61c7f91a4a1ae77', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字典类型的基本概念
Python语言中通过字典实现映射
Python语言中的字典可以通过大括号{}建立，建立模式如下：
{<键1>:<值1>, <键2>:<值2>, … , <键n>:<值n>}
键和值通过冒号连接
不同键值对通过逗号隔开
2024/9/2
数据结构
71') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11043, 43, 190, 183, 72, 0, '示例', '示例

示例
2024/9/2
数据结构
72

示例
2024/9/2
数据结构
72', 'cdf56095efe8517a3fc89013a7f77f54fccdc123fe2cbe2a989d49482c7a3f10', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '示例
2024/9/2
数据结构
72') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11044, 43, 190, 183, 73, 0, '字典的基本操作', '字典的基本操作

字典的基本操作
创建一个空字典
往字典中添加键-值对
访问字典中的值
修改字典中的值
删除键-值对
成员资格
2024/9/2
数据结构
73

字典的基本操作
创建一个空字典
往字典中添加键-值对
访问字典中的值
修改字典中的值
删除键-值对
成员资格
2024/9/2
数据结构
73', '9ff1294d40663f03b62a25a489e2d08da200ae9d2950c0e37dca9655aa939081', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '字典的基本操作
创建一个空字典
往字典中添加键-值对
访问字典中的值
修改字典中的值
删除键-值对
成员资格
2024/9/2
数据结构
73') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11045, 43, 190, 183, 74, 0, '创建一个空字典', '创建一个空字典

创建一个空字典
使用一对空的花括号定义一个空字典
2024/9/2
数据结构
74

创建一个空字典
使用一对空的花括号定义一个空字典
2024/9/2
数据结构
74', '359ba7842333f20e16b286c0eff8593f33517671cc10d2774df239485cac5ac8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '创建一个空字典
使用一对空的花括号定义一个空字典
2024/9/2
数据结构
74') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11046, 43, 190, 183, 75, 0, '添加键-值对', '添加键-值对

添加键-值对
字典是一种动态结构，可随时在其中添加键-值对，可依次指定字典名、用方括号括起来的键和相关联的值
通过中括号可以增加新的元素
2024/9/2
数据结构
75

添加键-值对
字典是一种动态结构，可随时在其中添加键-值对，可依次指定字典名、用方括号括起来的键和相关联的值
通过中括号可以增加新的元素
2024/9/2
数据结构
75', 'd84b2e1c05d794c951cc5dac0f9db9394dc10117cca09e496046721b68d3e5df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '添加键-值对
字典是一种动态结构，可随时在其中添加键-值对，可依次指定字典名、用方括号括起来的键和相关联的值
通过中括号可以增加新的元素
2024/9/2
数据结构
75') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11047, 43, 190, 183, 76, 0, '修改字典中的值', '修改字典中的值

修改字典中的值
要修改字典中的值，可依次指定字典名、用方括号括起的键以及与该键相关联的新值
2024/9/2
数据结构
76

修改字典中的值
要修改字典中的值，可依次指定字典名、用方括号括起的键以及与该键相关联的新值
2024/9/2
数据结构
76', '70c97604a0a56c15b8db90e503ab497fa36401005fbcd23b9fef30db1eb5cde0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '修改字典中的值
要修改字典中的值，可依次指定字典名、用方括号括起的键以及与该键相关联的新值
2024/9/2
数据结构
76') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11048, 43, 190, 183, 77, 0, '更新字典', '更新字典

更新字典
dict.update(adict)，将字典adict中的项（键:值）添加到dict中
Adict中所有的键值对都会被添加到dict中，如果有相同的键则会进行覆盖
2024/9/2
数据结构
77

更新字典
dict.update(adict)，将字典adict中的项（键:值）添加到dict中
Adict中所有的键值对都会被添加到dict中，如果有相同的键则会进行覆盖
2024/9/2
数据结构
77', '22eae1f127faa9e991ab19321be0d955074a4e5963a6c89715454bdd159cd3c7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '更新字典
dict.update(adict)，将字典adict中的项（键:值）添加到dict中
Adict中所有的键值对都会被添加到dict中，如果有相同的键则会进行覆盖
2024/9/2
数据结构
77') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11049, 43, 190, 183, 78, 0, '访问字典中的值', '访问字典中的值

访问字典中的值
字典最主要的用法是查找与特定键相对应的值
方法一：索引操作（根据键找值，键不存在则报错）
2024/9/2
数据结构
78
<值> = <字典变量>[<键>]

访问字典中的值
字典最主要的用法是查找与特定键相对应的值
方法一：索引操作（根据键找值，键不存在则报错）
2024/9/2
数据结构
78
<值> = <字典变量>[<键>]', '142517cdc8a1b3be05c5e403f23e077c608e06adcf599b3f91f01acf2188cb74', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '访问字典中的值
字典最主要的用法是查找与特定键相对应的值
方法一：索引操作（根据键找值，键不存在则报错）
2024/9/2
数据结构
78
<值> = <字典变量>[<键>]') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11050, 43, 190, 183, 79, 0, '访问字典中的值', '访问字典中的值

访问字典中的值
方法二：get函数操作（键找值，键不存在返回默认值）
2024/9/2
数据结构
79
<值> = <字典变量>.get(<键>,<默认值>)

访问字典中的值
方法二：get函数操作（键找值，键不存在返回默认值）
2024/9/2
数据结构
79
<值> = <字典变量>.get(<键>,<默认值>)', '3c5025654dc976723b1fb4335f4ad283c4c30bf9d80967be7cdedb12e0814e68', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '访问字典中的值
方法二：get函数操作（键找值，键不存在返回默认值）
2024/9/2
数据结构
79
<值> = <字典变量>.get(<键>,<默认值>)') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11051, 43, 190, 183, 80, 0, '删除键-值对', '删除键-值对

删除键-值对
del <d>[<key>]
2024/9/2
数据结构
80

删除键-值对
del <d>[<key>]
2024/9/2
数据结构
80', '8b153c6dcdbb48a42c3157bf67536d622aa10f9da25edc826ad449404155f52b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '删除键-值对
del <d>[<key>]
2024/9/2
数据结构
80') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11052, 43, 190, 183, 81, 0, '删除键-值对（2）', '删除键-值对（2）

删除键-值对（2）
<d>.pop(<key>)
2024/9/2
数据结构
81

删除键-值对（2）
<d>.pop(<key>)
2024/9/2
数据结构
81', 'aaba6db89591b7ba405eb2f3734a1974a62ff4f68c727cafb741fbc65cea723d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '删除键-值对（2）
<d>.pop(<key>)
2024/9/2
数据结构
81') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11053, 43, 190, 183, 82, 0, '删除键-值对（3）', '删除键-值对（3）

删除键-值对（3）
<d>.popitem()
随机从字典中取出一个键值对，以元组（key, value）形式返回
2024/9/2
数据结构
82

删除键-值对（3）
<d>.popitem()
随机从字典中取出一个键值对，以元组（key, value）形式返回
2024/9/2
数据结构
82', '005e5ac8bf255432b4637b227d3d05b8a2e49df73a3b46f476e856630294271b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '删除键-值对（3）
<d>.popitem()
随机从字典中取出一个键值对，以元组（key, value）形式返回
2024/9/2
数据结构
82') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11054, 43, 190, 183, 83, 0, '删除键-值对（4）', '删除键-值对（4）

删除键-值对（4）
<d>.clear()
清空所有的键-值对
2024/9/2
数据结构
83

删除键-值对（4）
<d>.clear()
清空所有的键-值对
2024/9/2
数据结构
83', 'a854f908460e39dd01d7c9ecd785115242e29004385c2c9900cc57faecc7fd4a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '删除键-值对（4）
<d>.clear()
清空所有的键-值对
2024/9/2
数据结构
83') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11055, 43, 190, 183, 84, 0, '成员资格', '成员资格

成员资格
<key> in <d>
如果键在字典中则返回True，否则返回False
2024/9/2
数据结构
84

成员资格
<key> in <d>
如果键在字典中则返回True，否则返回False
2024/9/2
数据结构
84', '1c12776547fe8bf150b726f05acbf36645ab38d0bbebae9eac3035078fc94d59', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '成员资格
<key> in <d>
如果键在字典中则返回True，否则返回False
2024/9/2
数据结构
84') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11056, 43, 190, 183, 85, 0, '遍历字典', '遍历字典

遍历字典
遍历所有的键-值对
遍历字典中的所有键
按顺序遍历字典中的所有键
遍历字典中的所有值
2024/9/2
数据结构
85

遍历字典
遍历所有的键-值对
遍历字典中的所有键
按顺序遍历字典中的所有键
遍历字典中的所有值
2024/9/2
数据结构
85', '61d08050f3c0e7e7653b38f522a66ee6165b21eeef9741a875b001931b832a4d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '遍历字典
遍历所有的键-值对
遍历字典中的所有键
按顺序遍历字典中的所有键
遍历字典中的所有值
2024/9/2
数据结构
85') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11057, 43, 190, 183, 86, 0, '返回所有键/值/键值对', '返回所有键/值/键值对

返回所有键/值/键值对
<d>.keys()，返回所有的键信息
<d>.values()，返回所有的值信息
<d>.items()，返回所有的键值对
2024/9/2
数据结构
86

返回所有键/值/键值对
<d>.keys()，返回所有的键信息
<d>.values()，返回所有的值信息
<d>.items()，返回所有的键值对
2024/9/2
数据结构
86', '8b716b61f7c68dcd6803efd06de9578ea54efea7340b6118eabb19c9aec6d4af', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '返回所有键/值/键值对
<d>.keys()，返回所有的键信息
<d>.values()，返回所有的值信息
<d>.items()，返回所有的键值对
2024/9/2
数据结构
86') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11058, 43, 190, 183, 87, 0, '遍历所有的键-值对', '遍历所有的键-值对

遍历所有的键-值对
for item in <d>.items():
for k, v in <d>.items():
2024/9/2
数据结构
87

遍历所有的键-值对
for item in <d>.items():
for k, v in <d>.items():
2024/9/2
数据结构
87', '3aab5ecf7d9bea11d0e61407e845bf969855d95b162057874a46a6bbb2e72af2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '遍历所有的键-值对
for item in <d>.items():
for k, v in <d>.items():
2024/9/2
数据结构
87') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11059, 43, 190, 183, 88, 0, '遍历字典中的所有键', '遍历字典中的所有键

遍历字典中的所有键
for k in <d>.keys():
for k in <d>:
2024/9/2
数据结构
88

遍历字典中的所有键
for k in <d>.keys():
for k in <d>:
2024/9/2
数据结构
88', '9c2d8235e5bf24127c04422830accdd838a5581984dfae14d91b4c0279f188ef', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '遍历字典中的所有键
for k in <d>.keys():
for k in <d>:
2024/9/2
数据结构
88') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11060, 43, 190, 183, 89, 0, '按顺序遍历字典中的所有键', '按顺序遍历字典中的所有键

按顺序遍历字典中的所有键
for k in sorted(<d>.keys()):
2024/9/2
数据结构
89

按顺序遍历字典中的所有键
for k in sorted(<d>.keys()):
2024/9/2
数据结构
89', 'db21290e14b864f98f29a3e183e6e564730a383254faaf87ced70b7e76057b21', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '按顺序遍历字典中的所有键
for k in sorted(<d>.keys()):
2024/9/2
数据结构
89') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11061, 43, 190, 183, 90, 0, '遍历字典中的所有值', '遍历字典中的所有值

遍历字典中的所有值
for v in <d>.values():
2024/9/2
数据结构
90

遍历字典中的所有值
for v in <d>.values():
2024/9/2
数据结构
90', '36dce306759f09564d0b9568c32f1b661a617a03afeb26577c6bbfab13090879', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '遍历字典中的所有值
for v in <d>.values():
2024/9/2
数据结构
90') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11062, 43, 190, 183, 91, 0, '举例：运算器', '举例：运算器

举例：运算器
输入两个数字，并输入加减乘除运算符号，输出运算结果。若输入其他符号，则退出程序
2024/9/2
数据结构
91

举例：运算器
输入两个数字，并输入加减乘除运算符号，输出运算结果。若输入其他符号，则退出程序
2024/9/2
数据结构
91', '140ccfdcfc5fc198f1762464c1c71803d4fee2c38aa8f7f8ee43e753873c2cb7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：运算器
输入两个数字，并输入加减乘除运算符号，输出运算结果。若输入其他符号，则退出程序
2024/9/2
数据结构
91') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11063, 43, 190, 183, 92, 0, '举例：运算器', '举例：运算器

举例：运算器
2024/9/2
数据结构
92

举例：运算器
2024/9/2
数据结构
92', '41efbcbf6a95dc8d88f303a779c1bf84999f51922a81cbbd1d592ba46e9bb919', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：运算器
2024/9/2
数据结构
92') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11064, 43, 190, 183, 93, 0, '举例：成绩统计', '举例：成绩统计

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
93
第一步，明确成绩统计的数据类型字典类型
{''A'': 3, ''B'': 4, ''F'': 3}
字典的键 --》 统计项
字典的值 --》 次数

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
93
第一步，明确成绩统计的数据类型字典类型
{''A'': 3, ''B'': 4, ''F'': 3}
字典的键 --》 统计项
字典的值 --》 次数', 'f4269abfbccc28eab0b50d4b2dac77ff96b49c05181a4e3fcf6947d484038a38', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
93
第一步，明确成绩统计的数据类型字典类型
{''A'': 3, ''B'': 4, ''F'': 3}
字典的键 --》 统计项
字典的值 --》 次数') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11065, 43, 190, 183, 94, 0, '举例：成绩统计', '举例：成绩统计

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
94
第二步，统计方式，逐个统计，来一个加一个

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
94
第二步，统计方式，逐个统计，来一个加一个', '0e2165f01204784766c429c2ed6038e47c9f93b9a9a9b02cd90b02bb265fd5ed', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
94
第二步，统计方式，逐个统计，来一个加一个') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11066, 43, 190, 183, 95, 0, '举例：成绩统计', '举例：成绩统计

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
95
第三步，输出结果，循环字典

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
95
第三步，输出结果，循环字典', '995a862221eb81f0b975b96bcefe997fd30f4da966f0141c89f2233915596510', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
A有3个
B有4个
F有3个
2024/9/2
数据结构
95
第三步，输出结果，循环字典') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11067, 43, 190, 183, 96, 0, '举例：成绩统计', '举例：成绩统计

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
2024/9/2
数据结构
96
scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]

score_dict = {}
for score in scores:
    if score in score_dict:
        score_dict[score] += 1
    else:
        score_dict[score] = 1
for score, count in score_dict.items():
    print("{}有{}个".format(score, count))
scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]

score_dict = {}
for score in scores:
    score_dict[score] = score_dict.get(score, 0) + 1 
for score, count in score_dict.items():
    print("{}有{}个".format(score, count))

举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
2024/9/2
数据结构
96
scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]

score_dict = {}
for score in scores:
    i…', 'f275d6cb2c02e774217552fddaaaab2bdf9bf26e277c11aab798f645ee5e2fac', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：成绩统计
某班级学生成绩分别为
["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]
统计成绩的分布情况
2024/9/2
数据结构
96
scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]

score_dict = {}
for score in scores:
    i…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11068, 43, 190, 183, 97, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
97

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
97', '5d85009a19f83ec86d7244d15ed9d86777a07ba0d6ac29555c5070a6e1cba88b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
97') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11069, 43, 190, 183, 98, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
98
第一步，明确通讯录的数据类型字典类型

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
98
第一步，明确通讯录的数据类型字典类型', '3e4869257e9c86e2466efb48538a8ca29d71da6e0769dd9c21e08684ebfd51dc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
98
第一步，明确通讯录的数据类型字典类型') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11070, 43, 190, 183, 99, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
99
第二步，搭建手机通讯录管理的基本框架
while循环+分支语句

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
99
第二步，搭建手机通讯录管理的基本框架
while循环+分支语句', '2e240e09afa556702c191d2b0bdb7304875f4363b6e6a49b2d3482a8692e52c6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
99
第二步，搭建手机通讯录管理的基本框架
while循环+分支语句') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11071, 43, 190, 183, 100, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
100
第三步，实现添加联系人功能
利用字典的添加元素操作

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
100
第三步，实现添加联系人功能
利用字典的添加元素操作', 'dac935a2f43b453765705c3844a0470a599b79562fdcb81b48c4bc8d60c6ddea', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
100
第三步，实现添加联系人功能
利用字典的添加元素操作') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11072, 43, 190, 183, 101, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
101
第四步，实现删除联系人功能
利用字典的删除元素操作

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
101
第四步，实现删除联系人功能
利用字典的删除元素操作', '553af7fd5e1d3f8500beaae722c2851202c5267718257bb6ea3c2ad352d86c8a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
101
第四步，实现删除联系人功能
利用字典的删除元素操作') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11073, 43, 190, 183, 102, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
102
第五步，实现查找联系人功能
利用字典的查找元素操作

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
102
第五步，实现查找联系人功能
利用字典的查找元素操作', '2f5bd5b751c3365b77aca7b6f24eccd3933328725e5472ae76e51530a95ce56a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
102
第五步，实现查找联系人功能
利用字典的查找元素操作') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11074, 43, 190, 183, 103, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
103
第六步，实现电话本显示功能
利用字典的遍历元素操作

举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
103
第六步，实现电话本显示功能
利用字典的遍历元素操作', '0adca08ef231cdb515ce6d07dd2a7f2d2e9457298e116e491f5ed13683eea538', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
实现通讯录功能：
1. 添加联系人  
2. 删除联系人 
3. 查找联系人 
4. 电话本显示 
0. 退出
2024/9/2
数据结构
103
第六步，实现电话本显示功能
利用字典的遍历元素操作') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11075, 43, 190, 183, 104, 0, '举例：手机通讯录管理', '举例：手机通讯录管理

举例：手机通讯录管理
2024/9/2
数据结构
104

举例：手机通讯录管理
2024/9/2
数据结构
104', '2eba75997a7fc03a13592d371b0cb5ce9fe50a6abe67cea93143da0e449c9544', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '举例：手机通讯录管理
2024/9/2
数据结构
104') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11076, 43, 190, 183, 105, 0, '2024/9/2', '2024/9/2

2024/9/2
数据结构
105
#手机通讯录管理V1
print("通讯录功能：1.添加联系人  2. 删除联系人 3:查找联系人 4:电话本显示 0:退出：")
contact_dict = {}
choice = input("请输入您要操作菜单的数字：")
while True:
    if choice == "1":
        name = input("请输入联系人姓名:")
        phone = input("请输入联系人电话:")
        contact_dict[name] = phone
        print("联系人{}已成功保存到通讯录!!".format(name))
        
    elif choice == "2":
        name = input("请输入要删除的联系人姓名:")
        if name in contact_dict:
            del contact_dict[name]
            print("联系人{}已删除!!".format(name))
        else:
            print("联系人{}不存在!!".format(name))

    elif choice == "3":
        name = input("请输入要查找的联系人姓名:")
        if name in contact_dict:
            print("联系人{}的电话为：{}".format(name, contact_dict[name]))
        else:
            print("联系人{}不存在!!".format(name))

    elif choice == "4":
        for name, phone in contact_dict.items():
            print("{}:{}".format(name, phone))

    elif choice == "0":
        break
    
    else:
        print("指令输入错误，请重新输入！")

    choice = input("请输入您要操作菜单的数字：")

2024/9/2
数据结构
105
#手机通讯录管理V1
print("通讯录功能：1.添加联系人  2. 删除联系人 3:查找联系人 4:电话本显示 0:退出：")
contact_dict = {}
choice = input("请输入您要操作菜单的数字：")
while True:
    if choice == "1":
        name = input("请输入联系人姓名:"…', '7576bfa7a37a4b678f2b38d00480a59c49c591b4852f321aa6d829714fc1dacc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第五章：数据结构.pdf', '2024/9/2
数据结构
105
#手机通讯录管理V1
print("通讯录功能：1.添加联系人  2. 删除联系人 3:查找联系人 4:电话本显示 0:退出：")
contact_dict = {}
choice = input("请输入您要操作菜单的数字：")
while True:
    if choice == "1":
        name = input("请输入联系人姓名:"…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11077, 43, 191, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11078, 43, 191, NULL, 2, 0, '第六章：数据存取与分析', '第六章：数据存取与分析

第六章：数据存取与分析

第六章：数据存取与分析', '7c42b9af0cb3ba7fe48a5a19adb947fe9facf8d94b2a948aea5a41bd774c9dac', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第六章：数据存取与分析') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11079, 43, 191, NULL, 3, 0, '引言', '引言

引言
信息化的今天，来自各行业的大量数据都需要存储
数据存储的方式、技术有很多
文件
数据库
云存储
光盘/磁带
…
2025/4/1
数据存取与分析
3

引言
信息化的今天，来自各行业的大量数据都需要存储
数据存储的方式、技术有很多
文件
数据库
云存储
光盘/磁带
…
2025/4/1
数据存取与分析
3', 'b098a8f42a0280393b6dad37b9ffc6928b55c5319c60a3f45026be5a84dae9bc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '引言
信息化的今天，来自各行业的大量数据都需要存储
数据存储的方式、技术有很多
文件
数据库
云存储
光盘/磁带
…
2025/4/1
数据存取与分析
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11080, 43, 191, NULL, 4, 0, '目录', '目录

目录
6.1、文件系统概述
6.2、文件操作
6.3、基于文件的数据分析
2025/4/1
数据存取与分析
4

目录
6.1、文件系统概述
6.2、文件操作
6.3、基于文件的数据分析
2025/4/1
数据存取与分析
4', '526c7e3e53471835d4e4ef0c59b4fe06c16bd6a4d35a0ff78a3eb3c5e59f2c2e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '目录
6.1、文件系统概述
6.2、文件操作
6.3、基于文件的数据分析
2025/4/1
数据存取与分析
4') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11081, 43, 191, NULL, 5, 0, '6.1、文件系统概述', '6.1、文件系统概述

6.1、文件系统概述
2025/4/1
数据存取与分析
5

6.1、文件系统概述
2025/4/1
数据存取与分析
5', 'a103e1cda8717626f4a2c446777431b1d0ec3fc6f0dc0ce21361d2b32e995863', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '6.1、文件系统概述
2025/4/1
数据存取与分析
5') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11082, 43, 191, 184, 6, 0, '文件系统', '文件系统

文件系统
文件
文件是存储在辅助存储器上的数据序列
可以包含任何数据内容

目录（文件夹）
文件是通过目录来组织和管理的
目录一般采用树状结果
每个磁盘都有一个根目录
它包含若干文件和子目录
2025/4/1
数据存取与分析
6

文件系统
文件
文件是存储在辅助存储器上的数据序列
可以包含任何数据内容

目录（文件夹）
文件是通过目录来组织和管理的
目录一般采用树状结果
每个磁盘都有一个根目录
它包含若干文件和子目录
2025/4/1
数据存取与分析
6', '4badc3df9163302898467d47ed3a4830fcff42ed72ce05f6125096434e349420', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文件系统
文件
文件是存储在辅助存储器上的数据序列
可以包含任何数据内容

目录（文件夹）
文件是通过目录来组织和管理的
目录一般采用树状结果
每个磁盘都有一个根目录
它包含若干文件和子目录
2025/4/1
数据存取与分析
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11083, 43, 191, 184, 7, 0, '文件分类', '文件分类

文件分类
文本文件
由单一特定编码的字符组成，如:UTF-8编码
大部分文本文件都可以通过文本编辑软件或文字处理软件创建、修改和阅读，如：txt文件
存在统一字符编码，可以看作是存储在磁盘上的长字符串
二进制文件
直接由比特0和比特1组成
文件内部数据的组织格式与文件用途有关，是信息按照非字符但特定格式形成的文件，如：png格式的图片文件、avi格式的视频文件
没有统一字符编码，只能当作字节流
2025/4/1
数据存取与分析
7

文件分类
文本文件
由单一特定编码的字符组成，如:UTF-8编码
大部分文本文件都可以通过文本编辑软件或文字处理软件创建、修改和阅读，如：txt文件
存在统一字符编码，可以看作是存储在磁盘上的长字符串
二进制文件
直接由比特0和比特1组成
文件内部数据的组织格式与文件用途有关，是信息按照非字符但特定格式形成的文件，如：png格式的图片文件、avi格式的视频文件
没有统一字符编码，只能当作字节流
2…', '6f36ebef49ac993ba5237ca3ce3acb6c1a21a655aa3f7f6104da323be6bcca47', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文件分类
文本文件
由单一特定编码的字符组成，如:UTF-8编码
大部分文本文件都可以通过文本编辑软件或文字处理软件创建、修改和阅读，如：txt文件
存在统一字符编码，可以看作是存储在磁盘上的长字符串
二进制文件
直接由比特0和比特1组成
文件内部数据的组织格式与文件用途有关，是信息按照非字符但特定格式形成的文件，如：png格式的图片文件、avi格式的视频文件
没有统一字符编码，只能当作字节流
2…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11084, 43, 191, 184, 8, 0, '文本文件和二进制文件的区别', '文本文件和二进制文件的区别

文本文件和二进制文件的区别
是否有统一的字符编码
文本文件是基于字符编码的文件，内容是字符
二进制文件的内容则是二进制数据
是否具有可读性
文本文件通过文本编辑软件可以浏览，存取时需要编码和解码
二进制文件无法直接读懂，存取时不需要编码和解码
2025/4/1
数据存取与分析
8

文本文件和二进制文件的区别
是否有统一的字符编码
文本文件是基于字符编码的文件，内容是字符
二进制文件的内容则是二进制数据
是否具有可读性
文本文件通过文本编辑软件可以浏览，存取时需要编码和解码
二进制文件无法直接读懂，存取时不需要编码和解码
2025/4/1
数据存取与分析
8', '26b309278fa5da186eacaf5c05faafbe090ba596841e74bdcdea2e5f57404707', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文本文件和二进制文件的区别
是否有统一的字符编码
文本文件是基于字符编码的文件，内容是字符
二进制文件的内容则是二进制数据
是否具有可读性
文本文件通过文本编辑软件可以浏览，存取时需要编码和解码
二进制文件无法直接读懂，存取时不需要编码和解码
2025/4/1
数据存取与分析
8') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11085, 43, 191, 184, 9, 0, '文件的编码', '文件的编码

文件的编码
文本文件可以在Windows记事本中打开并选择编码方式保存
2025/4/1
数据存取与分析
9

文件的编码
文本文件可以在Windows记事本中打开并选择编码方式保存
2025/4/1
数据存取与分析
9', '4cba07e4520b267f20b5e5bebfabe22999c594052faa199621a44aea5f3bd522', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文件的编码
文本文件可以在Windows记事本中打开并选择编码方式保存
2025/4/1
数据存取与分析
9') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11086, 43, 191, 184, 10, 0, '文件的编码', '文件的编码

文件的编码
str.encode() 可以查看unicode值编码后的结果
bytes.decode()可以查看解码后的结果
2025/4/1
数据存取与分析
10

文件的编码
str.encode() 可以查看unicode值编码后的结果
bytes.decode()可以查看解码后的结果
2025/4/1
数据存取与分析
10', 'c73e1ed13f93df851553c4b86a41014ec6f01d828c4366a0d198c4f52d4126ea', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文件的编码
str.encode() 可以查看unicode值编码后的结果
bytes.decode()可以查看解码后的结果
2025/4/1
数据存取与分析
10') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11087, 43, 191, 184, 11, 0, 'TXT文件', 'TXT文件

TXT文件
TXT文件本质上是一个字符串序列
换行符用“\n”表示
2025/4/1
数据存取与分析
11

TXT文件
TXT文件本质上是一个字符串序列
换行符用“\n”表示
2025/4/1
数据存取与分析
11', 'cccf46497a2473bd81e1c749db7ce944042723b70583696e68c21008748cc60a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', 'TXT文件
TXT文件本质上是一个字符串序列
换行符用“\n”表示
2025/4/1
数据存取与分析
11') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11088, 43, 191, NULL, 12, 0, '6.2、文件操作', '6.2、文件操作

6.2、文件操作
2025/4/1
数据存取与分析
12

6.2、文件操作
2025/4/1
数据存取与分析
12', 'b43c0ce1384eb4cdcdbff069d2541bed8acc56f71700dd5a148bed3b3bc8b651', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '6.2、文件操作
2025/4/1
数据存取与分析
12') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11089, 43, 191, NULL, 13, 0, '操作文件', '操作文件

操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
13

操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
13', '9f9bd7f7946281c5ae52dd9855622cb6cf0ad52a94dc8064c59b26e1bc688910', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11090, 43, 191, NULL, 14, 0, '操作文件', '操作文件

操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
14

操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
14', 'c05b9883aebbf0e1d765cb6ea5b5dcc7ee0a4be20fe658be53b41c7658f01413', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '操作文件
举例：把大象关进冰箱
2025/4/1
数据存取与分析
14') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11091, 43, 191, 184, 15, 0, '操作文件', '操作文件

操作文件
Python对文本文件和二进制文件采用统一的操作步骤，即“打开-操作-关闭”
2025/4/1
数据存取与分析
15
1、打开文件
2、操作文件
3、关闭文件

操作文件
Python对文本文件和二进制文件采用统一的操作步骤，即“打开-操作-关闭”
2025/4/1
数据存取与分析
15
1、打开文件
2、操作文件
3、关闭文件', '338ea56b952fdc9680e5599a52973642976c952403cf6eb02da61143057c74d5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '操作文件
Python对文本文件和二进制文件采用统一的操作步骤，即“打开-操作-关闭”
2025/4/1
数据存取与分析
15
1、打开文件
2、操作文件
3、关闭文件') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11092, 43, 191, 184, 16, 0, '第一步：打开文件', '第一步：打开文件

第一步：打开文件
Python通过解释器内置的open()函数打开一个文件，并实现该文件与一个程序变量的关联，open()函数格式如下：

<变量名> = open(<文件地址>, <打开模式>,<编码模式>)

   open()函数有三个重要的参数：文件地址，打开模式，编码模式。
2025/4/1
数据存取与分析
16

第一步：打开文件
Python通过解释器内置的open()函数打开一个文件，并实现该文件与一个程序变量的关联，open()函数格式如下：

<变量名> = open(<文件地址>, <打开模式>,<编码模式>)

   open()函数有三个重要的参数：文件地址，打开模式，编码模式。
2025/4/1
数据存取与分析
16', '909ec66c767b4f6d9b1e90064785deee21b83af18405dc4d93e4f506be8b9de4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第一步：打开文件
Python通过解释器内置的open()函数打开一个文件，并实现该文件与一个程序变量的关联，open()函数格式如下：

<变量名> = open(<文件地址>, <打开模式>,<编码模式>)

   open()函数有三个重要的参数：文件地址，打开模式，编码模式。
2025/4/1
数据存取与分析
16') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11093, 43, 191, 184, 17, 0, '文件地址：绝对路径和相对路径', '文件地址：绝对路径和相对路径

文件地址：绝对路径和相对路径
绝对路径
从根目录开始到TXT文件所在完整路径
举例：
中国->上海市-> 松江区->人民北路2999号（东华大学）->第二教学楼2225
相对路径
从python文件到TXT文件之间的路径
举例：
人：人民北路2999号（东华大学）
相对地址：第二教学楼2225
2025/4/1
数据存取与分析
17

文件地址：绝对路径和相对路径
绝对路径
从根目录开始到TXT文件所在完整路径
举例：
中国->上海市-> 松江区->人民北路2999号（东华大学）->第二教学楼2225
相对路径
从python文件到TXT文件之间的路径
举例：
人：人民北路2999号（东华大学）
相对地址：第二教学楼2225
2025/4/1
数据存取与分析
17', '51f4e7c2c32fd814185464e9b6550676c382bcd31853ba5ac696a29cec50c42a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '文件地址：绝对路径和相对路径
绝对路径
从根目录开始到TXT文件所在完整路径
举例：
中国->上海市-> 松江区->人民北路2999号（东华大学）->第二教学楼2225
相对路径
从python文件到TXT文件之间的路径
举例：
人：人民北路2999号（东华大学）
相对地址：第二教学楼2225
2025/4/1
数据存取与分析
17') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11094, 43, 191, 184, 18, 0, '绝对路径示例', '绝对路径示例

绝对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，那么包含绝对路径的文件名是由磁盘驱动器、目录层次和文件名三部分组成的D:\python7\test1\file.txt
在python中用字符串表示为：
D:\\python7\\test1\\file.txt
D:/python7/test1/file.txt
2025/4/1
数据存取与分析
18

绝对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，那么包含绝对路径的文件名是由磁盘驱动器、目录层次和文件名三部分组成的D:\python7\test1\file.txt
在python中用字符串表示为：
D:\\python7\\test1\\file.txt
D:/python7/test1/file.txt
2025/4/1
数据存取与分析
18', '1e8bc8301802da452dc07fe61633f29261845174817367aa6e29786bcb7708b4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '绝对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，那么包含绝对路径的文件名是由磁盘驱动器、目录层次和文件名三部分组成的D:\python7\test1\file.txt
在python中用字符串表示为：
D:\\python7\\test1\\file.txt
D:/python7/test1/file.txt
2025/4/1
数据存取与分析
18') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11095, 43, 191, 184, 19, 0, '相对路径示例', '相对路径示例

相对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，源程序保存在D盘python7文件夹下，那么包含相对路径的文件名是test1\file.txt
在python中用字符串表示为：
test1\\file.txt
test1/file.txt
2025/4/1
数据存取与分析
19

相对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，源程序保存在D盘python7文件夹下，那么包含相对路径的文件名是test1\file.txt
在python中用字符串表示为：
test1\\file.txt
test1/file.txt
2025/4/1
数据存取与分析
19', '8c909017c347a0828d018165182dacd9b4bd130232345d3545c9f50a00e4c8d3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '相对路径示例
假设文件file.txt保存在D盘python7文件夹的test1子文件夹下，源程序保存在D盘python7文件夹下，那么包含相对路径的文件名是test1\file.txt
在python中用字符串表示为：
test1\\file.txt
test1/file.txt
2025/4/1
数据存取与分析
19') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11096, 43, 191, 184, 20, 0, '打开模式', '打开模式

打开模式
open()函数提供7种基本的打开模式，只需了解3个
2025/4/1
数据存取与分析
20

打开模式
open()函数提供7种基本的打开模式，只需了解3个
2025/4/1
数据存取与分析
20', '06d4f37e7858a54a36d57598e21559bfc57f572df5f21a4b2b090de067bd5843', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '打开模式
open()函数提供7种基本的打开模式，只需了解3个
2025/4/1
数据存取与分析
20') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11097, 43, 191, 184, 21, 0, '例子', '例子

例子
textfile=open(“7.1.txt”, “rt”)
textfile=open(“7.1.txt”, “r”)
textfile=open(“7.1.txt”)
Binfile=open(“music.mp3”, “rb”)
2025/4/1
数据存取与分析
21

例子
textfile=open(“7.1.txt”, “rt”)
textfile=open(“7.1.txt”, “r”)
textfile=open(“7.1.txt”)
Binfile=open(“music.mp3”, “rb”)
2025/4/1
数据存取与分析
21', 'c805a05d4e37f8a2faf9ced6cbf721f3fb263d74f66db03aaa8cb246957113a4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '例子
textfile=open(“7.1.txt”, “rt”)
textfile=open(“7.1.txt”, “r”)
textfile=open(“7.1.txt”)
Binfile=open(“music.mp3”, “rb”)
2025/4/1
数据存取与分析
21') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11098, 43, 191, 184, 22, 0, '编码模式', '编码模式

编码模式
不同操作系统默认的文件编码方式不同
Windows：ANSI
MacOS：UTF-8

通过以下语句查看Python系统编码方式
#获取系统默认编码：
import sys
print(sys.getdefaultencoding())
2025/4/1
数据存取与分析
22

编码模式
不同操作系统默认的文件编码方式不同
Windows：ANSI
MacOS：UTF-8

通过以下语句查看Python系统编码方式
#获取系统默认编码：
import sys
print(sys.getdefaultencoding())
2025/4/1
数据存取与分析
22', '23b3644ed7e0a2da32b7204ae442306ab3258928481397df3374f6bb775d41a4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '编码模式
不同操作系统默认的文件编码方式不同
Windows：ANSI
MacOS：UTF-8

通过以下语句查看Python系统编码方式
#获取系统默认编码：
import sys
print(sys.getdefaultencoding())
2025/4/1
数据存取与分析
22') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11099, 43, 191, 184, 23, 0, '编码模式', '编码模式

编码模式
文件中的两种重要的编码模式
ANSI（默认值）
UTF-8 （设置encoding=“utf-8”）
想象成不同的语言就行
2025/4/1
数据存取与分析
23

编码模式
文件中的两种重要的编码模式
ANSI（默认值）
UTF-8 （设置encoding=“utf-8”）
想象成不同的语言就行
2025/4/1
数据存取与分析
23', '52b7306ae520be247aa7c7405365e67252a9a1d878f9dd0430669098160b9db6', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '编码模式
文件中的两种重要的编码模式
ANSI（默认值）
UTF-8 （设置encoding=“utf-8”）
想象成不同的语言就行
2025/4/1
数据存取与分析
23') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11100, 43, 191, 184, 24, 0, '编码模式', '编码模式

编码模式
当文件为ANSI格式时：

当文件为UTF-8格式时：
不设置encoding参数会报错


设置encoding参数，程序正常执行
f1 = open("test1.txt",''r'')
f1 = open("test1.txt",''r'',encoding = "utf-8")
2025/4/1
数据存取与分析
24

编码模式
当文件为ANSI格式时：

当文件为UTF-8格式时：
不设置encoding参数会报错


设置encoding参数，程序正常执行
f1 = open("test1.txt",''r'')
f1 = open("test1.txt",''r'',encoding = "utf-8")
2025/4/1
数据存取与分析
24', '82be87927c07812cd97d06f35055929b5307268da2679cc191437e8a618f17d8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '编码模式
当文件为ANSI格式时：

当文件为UTF-8格式时：
不设置encoding参数会报错


设置encoding参数，程序正常执行
f1 = open("test1.txt",''r'')
f1 = open("test1.txt",''r'',encoding = "utf-8")
2025/4/1
数据存取与分析
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11101, 43, 191, 184, 25, 0, '第三步：文件的关闭', '第三步：文件的关闭

第三步：文件的关闭
<file>.close()
2025/4/1
数据存取与分析
25

第三步：文件的关闭
<file>.close()
2025/4/1
数据存取与分析
25', 'bc584f039f4ac34f220fb38f5b14fe061a70e0657ff697934d13eb57dc39323a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第三步：文件的关闭
<file>.close()
2025/4/1
数据存取与分析
25') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11102, 43, 191, 184, 26, 0, '第二步：文件的读取', '第二步：文件的读取

第二步：文件的读取
2个常用的文件内容读取方法
2025/4/1
数据存取与分析
26

第二步：文件的读取
2个常用的文件内容读取方法
2025/4/1
数据存取与分析
26', '3ef8425ec8501b6555cbd158584dff2804ccae9b29a3ffac72e1b8f9aeba8eb4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第二步：文件的读取
2个常用的文件内容读取方法
2025/4/1
数据存取与分析
26') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11103, 43, 191, 184, 27, 0, '<file>.read()', '<file>.read()

<file>.read()
读入全部文本内容，返回结果为一个字符串
文件有多行内容，行尾的换行符也会读进来
2025/4/1
数据存取与分析
27

<file>.read()
读入全部文本内容，返回结果为一个字符串
文件有多行内容，行尾的换行符也会读进来
2025/4/1
数据存取与分析
27', '4f571c9ab97acf1c9cf8ba6c1a6b3ba25a39998a996ae75121ceb6190ac46d00', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '<file>.read()
读入全部文本内容，返回结果为一个字符串
文件有多行内容，行尾的换行符也会读进来
2025/4/1
数据存取与分析
27') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11104, 43, 191, 184, 28, 0, '<file>.readlines()', '<file>.readlines()

<file>.readlines()
读入全部文本内容，返回结果为一个列表
列表中的每个元素为一行文本字符串
2025/4/1
数据存取与分析
28

<file>.readlines()
读入全部文本内容，返回结果为一个列表
列表中的每个元素为一行文本字符串
2025/4/1
数据存取与分析
28', '5fe4debe82a9c2d314af81605fc95de26ddcf4884f85e5b6695f7b78680d0492', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '<file>.readlines()
读入全部文本内容，返回结果为一个列表
列表中的每个元素为一行文本字符串
2025/4/1
数据存取与分析
28') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11105, 43, 191, 184, 29, 0, '快速列表访问', '快速列表访问

快速列表访问
只需读入数据，Python提供了快速列表访问方式：
2025/4/1
数据存取与分析
29
<列表>=list(open(<文件名>))

快速列表访问
只需读入数据，Python提供了快速列表访问方式：
2025/4/1
数据存取与分析
29
<列表>=list(open(<文件名>))', '319e08bd3f44aff75a5a0b7b522cdebc590e7135e362bb5f2f81b13f6349d70c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '快速列表访问
只需读入数据，Python提供了快速列表访问方式：
2025/4/1
数据存取与分析
29
<列表>=list(open(<文件名>))') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11106, 43, 191, 184, 30, 0, 'for循环+文件', 'for循环+文件

for循环+文件
每次循环读取文件的一行
2025/4/1
数据存取与分析
30
方法一
方法二

for循环+文件
每次循环读取文件的一行
2025/4/1
数据存取与分析
30
方法一
方法二', '8632a2d282a08c14d9c7449bc4f138268bb3d1948dba2dc68c16fa83da9e5f5c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', 'for循环+文件
每次循环读取文件的一行
2025/4/1
数据存取与分析
30
方法一
方法二') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11107, 43, 191, 184, 31, 0, '处理换行符/空格', '处理换行符/空格

处理换行符/空格
去除每行首尾的空格/换行符
<str>.strip()
<str>.lstrip()
<str>.rstrip()
2025/4/1
数据存取与分析
31

处理换行符/空格
去除每行首尾的空格/换行符
<str>.strip()
<str>.lstrip()
<str>.rstrip()
2025/4/1
数据存取与分析
31', 'b3c2f67cc05e55238cdaf6772e36f9ad7999947371da25e022868ac0e486b929', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '处理换行符/空格
去除每行首尾的空格/换行符
<str>.strip()
<str>.lstrip()
<str>.rstrip()
2025/4/1
数据存取与分析
31') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11108, 43, 191, 184, 32, 0, '处理每行内容', '处理每行内容

处理每行内容
根据分隔符进行分割，返回值为列表
<str>.split(<分隔符>)
2025/4/1
数据存取与分析
32
students.txt

处理每行内容
根据分隔符进行分割，返回值为列表
<str>.split(<分隔符>)
2025/4/1
数据存取与分析
32
students.txt', '459b5ef6bc937a2aac1a403e0b36fa7f2ccf5005a0d5531526a0103b6d121036', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '处理每行内容
根据分隔符进行分割，返回值为列表
<str>.split(<分隔符>)
2025/4/1
数据存取与分析
32
students.txt') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11109, 43, 191, 184, 33, 0, '第二步+：将数据写入文件（w操作）', '第二步+：将数据写入文件（w操作）

第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
2025/4/1
数据存取与分析
33

第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
2025/4/1
数据存取与分析
33', '4548c2e563678974dc873df317e90247818c65caf241f964f8076ed1522b7179', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
2025/4/1
数据存取与分析
33') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11110, 43, 191, 184, 34, 0, '第二步+：将数据写入文件（w操作）', '第二步+：将数据写入文件（w操作）

第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
write()函数写法和print()函数类似
区别在于write()函数不会自动换行
2025/4/1
数据存取与分析
34

第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
write()函数写法和print()函数类似
区别在于write()函数不会自动换行
2025/4/1
数据存取与分析
34', 'd2a193e409d9c114e027123884ed3f9e8b088d8093b6c3f09b782b43858fd79d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第二步+：将数据写入文件（w操作）
f.write(string)
将字符串string的内容写到新文件f对应的文件中
write()函数写法和print()函数类似
区别在于write()函数不会自动换行
2025/4/1
数据存取与分析
34') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11111, 43, 191, 184, 35, 0, '第二步+：将数据写入文件（a操作）', '第二步+：将数据写入文件（a操作）

第二步+：将数据写入文件（a操作）
f.write(string)
将字符串string的内容追加写到f对应的文件中
2025/4/1
数据存取与分析
35

第二步+：将数据写入文件（a操作）
f.write(string)
将字符串string的内容追加写到f对应的文件中
2025/4/1
数据存取与分析
35', 'd4a84f12ec22b54d5cd9a96186a957d108399617798813ca3a0332d08312ea0d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '第二步+：将数据写入文件（a操作）
f.write(string)
将字符串string的内容追加写到f对应的文件中
2025/4/1
数据存取与分析
35') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11112, 43, 191, 184, 36, 0, '问题', '问题

问题
给定一篇文章，统计其中共出现了多少词，每个词出现的次数
2025/4/1
数据存取与分析
36
举例：词频统计

问题
给定一篇文章，统计其中共出现了多少词，每个词出现的次数
2025/4/1
数据存取与分析
36
举例：词频统计', 'a01ec5a186373ee357febb39b61d04f29a3b0db709f74be528c3cd5be3fd90b1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '问题
给定一篇文章，统计其中共出现了多少词，每个词出现的次数
2025/4/1
数据存取与分析
36
举例：词频统计') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11113, 43, 191, 184, 37, 0, '解题思路', '解题思路

解题思路
词频统计是一个累加问题，即对文档中的每个词设计一个计数器，词语每出现一次，相关计数器加1
以词语为键（key），计数器为值（value），构成<单词>:<出现次数>的键值对
2025/4/1
数据存取与分析
37

解题思路
词频统计是一个累加问题，即对文档中的每个词设计一个计数器，词语每出现一次，相关计数器加1
以词语为键（key），计数器为值（value），构成<单词>:<出现次数>的键值对
2025/4/1
数据存取与分析
37', '9c3314e3bd04dcf944f98171e28770f49080b153e09ca534fb4364a436d1461b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '解题思路
词频统计是一个累加问题，即对文档中的每个词设计一个计数器，词语每出现一次，相关计数器加1
以词语为键（key），计数器为值（value），构成<单词>:<出现次数>的键值对
2025/4/1
数据存取与分析
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11114, 43, 191, 184, 38, 0, '关键步骤1', '关键步骤1

关键步骤1
分解并提取英文文章的单词
单词怎么提取？
英文单词的分隔符可以是空格、标点符号或特殊符号等，用单一分割符无法将单词完全分割
解决办法：先将其他标点符号和特殊符号都转化为空格，再统一用空格分割
大小写要不要区分？
同一个单词会存在大小写形式，python是大小写区分，计数会出问题
解决办法：将所有单词统一为小写
2025/4/1
数据存取与分析
38

关键步骤1
分解并提取英文文章的单词
单词怎么提取？
英文单词的分隔符可以是空格、标点符号或特殊符号等，用单一分割符无法将单词完全分割
解决办法：先将其他标点符号和特殊符号都转化为空格，再统一用空格分割
大小写要不要区分？
同一个单词会存在大小写形式，python是大小写区分，计数会出问题
解决办法：将所有单词统一为小写
2025/4/1
数据存取与分析
38', 'e840ce24220d75758fffcfbff174364e559e901a3b3135f6fb9b7074949a329d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '关键步骤1
分解并提取英文文章的单词
单词怎么提取？
英文单词的分隔符可以是空格、标点符号或特殊符号等，用单一分割符无法将单词完全分割
解决办法：先将其他标点符号和特殊符号都转化为空格，再统一用空格分割
大小写要不要区分？
同一个单词会存在大小写形式，python是大小写区分，计数会出问题
解决办法：将所有单词统一为小写
2025/4/1
数据存取与分析
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11115, 43, 191, 184, 39, 0, '关键步骤2', '关键步骤2

关键步骤2
对每个单词进行计数
设计怎么样的数据结构来进行计数？
字典 counts = {}
Key: word 单词
Value: 出现次数
如何计数？
如果word已经出现在字典的key中 
if word in counts
counts[word] = counts[word] + 1
如果word是一个新词，没在字典的key中 
if word not in counts
counts[word] = 1
2025/4/1
数据存取与分析
39

关键步骤2
对每个单词进行计数
设计怎么样的数据结构来进行计数？
字典 counts = {}
Key: word 单词
Value: 出现次数
如何计数？
如果word已经出现在字典的key中 
if word in counts
counts[word] = counts[word] + 1
如果word是一个新词，没在字典的key中 
if word not in counts
counts…', '67c4fca4c1d2c29f9c3051d6b12dd5112e6c26d53c2239b23194a409cc378c80', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '关键步骤2
对每个单词进行计数
设计怎么样的数据结构来进行计数？
字典 counts = {}
Key: word 单词
Value: 出现次数
如何计数？
如果word已经出现在字典的key中 
if word in counts
counts[word] = counts[word] + 1
如果word是一个新词，没在字典的key中 
if word not in counts
counts…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11116, 43, 191, 184, 40, 0, '计数方法优化', '计数方法优化

计数方法优化
统一表示方法




简洁表示方法
counts.get(word, 0)的意思为，如果word在counts中，则返回键word对应的值，如果word不在counts中，则返回0
if word in counts:

    counts[word] = counts[word] + 1

else:

    counts[word] = 1
counts[word] = counts.get(word, 0) + 1
2025/4/1
数据存取与分析
40

计数方法优化
统一表示方法




简洁表示方法
counts.get(word, 0)的意思为，如果word在counts中，则返回键word对应的值，如果word不在counts中，则返回0
if word in counts:

    counts[word] = counts[word] + 1

else:

    counts[word] = 1
counts[word] = co…', 'bc1b13c22bc043d37e0e2fdb1c09b858f3d60e1f6ec4005ce47154a4fe0d1957', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '计数方法优化
统一表示方法




简洁表示方法
counts.get(word, 0)的意思为，如果word在counts中，则返回键word对应的值，如果word不在counts中，则返回0
if word in counts:

    counts[word] = counts[word] + 1

else:

    counts[word] = 1
counts[word] = co…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11117, 43, 191, 184, 41, 0, '关键步骤3', '关键步骤3

关键步骤3
对单词的统计值从高到底排序
由于字典类型没有顺序，需要将其转换为由顺序的列表类型
items = list(counts.items())
列表中的每个元素为一个（key,value）的元组
[(''the'', 1138), (''tragedy'', 3), (''of'', 669), (''hamlet'', 462)]

再使用sort()方法和lambda函数配合实现根据单词属性的次数对元素进行排序
items.sort(key=lambda x:x[1], reverse = True)
2025/4/1
数据存取与分析
41

关键步骤3
对单词的统计值从高到底排序
由于字典类型没有顺序，需要将其转换为由顺序的列表类型
items = list(counts.items())
列表中的每个元素为一个（key,value）的元组
[(''the'', 1138), (''tragedy'', 3), (''of'', 669), (''hamlet'', 462)]

再使用sort()方法和lambda函数配合实现根据单词属性的次数对元…', '3e265625bc6e1ed1be94a3cce1b64efa44a13b4d3a1c8aca09f581814838e18b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '关键步骤3
对单词的统计值从高到底排序
由于字典类型没有顺序，需要将其转换为由顺序的列表类型
items = list(counts.items())
列表中的每个元素为一个（key,value）的元组
[(''the'', 1138), (''tragedy'', 3), (''of'', 669), (''hamlet'', 462)]

再使用sort()方法和lambda函数配合实现根据单词属性的次数对元…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11118, 43, 191, 184, 42, 0, '*字典排序例子', '*字典排序例子

*字典排序例子
2025/4/1
数据存取与分析
42

*字典排序例子
2025/4/1
数据存取与分析
42', '061567467d5231d90efc0f02e3450483344c56b7831ad297226f0120549c6a2e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '*字典排序例子
2025/4/1
数据存取与分析
42') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11119, 43, 191, 184, 43, 0, '词频统计代码实现', '词频统计代码实现

词频统计代码实现
2025/4/1
数据存取与分析
43

词频统计代码实现
2025/4/1
数据存取与分析
43', '23d304ac17f2f09061c8fb049c10e2fcc1785f1eca837082ac0f4ca488af0d43', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '词频统计代码实现
2025/4/1
数据存取与分析
43') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11120, 43, 191, 184, 44, 0, '2025/4/1', '2025/4/1

2025/4/1
数据存取与分析
44
path1 = "lemontree.txt"
f1 = open(path1, ''r'')
content = f1.read()
content = content.lower()
for ch in ['','','':'',''.'']:
    content = content.replace(ch, "")

words = content.split()
counts = {}
for word in words:
    counts[word] = counts.get(word, 0) + 1

items = list(counts.items())
items.sort(key=lambda x:x[1], reverse = True)
for i in range(10):
    word, count = items[i]
    print("{}==={}".format(word, count))

2025/4/1
数据存取与分析
44
path1 = "lemontree.txt"
f1 = open(path1, ''r'')
content = f1.read()
content = content.lower()
for ch in ['','','':'',''.'']:
    content = content.replace(ch, "")

words = content.split()
…', '6c2306e50cc0bba14faed03cc992f867e0ac5ab7a0b0fe04f39efbb259896775', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '2025/4/1
数据存取与分析
44
path1 = "lemontree.txt"
f1 = open(path1, ''r'')
content = f1.read()
content = content.lower()
for ch in ['','','':'',''.'']:
    content = content.replace(ch, "")

words = content.split()
…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11121, 43, 191, 184, 45, 0, '6.3、基于文件的数据分析', '6.3、基于文件的数据分析

6.3、基于文件的数据分析
2025/4/1
数据存取与分析
45

6.3、基于文件的数据分析
2025/4/1
数据存取与分析
45', 'bbc1a3588f680b143184cd0b7130273f07ac06585ab2f0aa89ba036dcd7106ec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '6.3、基于文件的数据分析
2025/4/1
数据存取与分析
45') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11122, 43, 191, 184, 46, 0, '举例：统计学科等级水平', '举例：统计学科等级水平

举例：统计学科等级水平
根据考试成绩，统计学科等级水平。
分析：某中学对学生的附加科目进行能力测试，并按以下标准统计学科等级水平。
（1）生物和科学两门课都达到60分，总分达到180分为及格；
（2）每门课达到85分，总分达到260分为优秀；
（3）总分不到180分或有任意一门课不到60分，为不及格。
学生成绩原始数据如图所示。
编程要求：从用制表符(\t)分割的score.txt文件中读取学生成绩数据，判定等级并写入level.txt文件中。
2025/4/1
数据存取与分析
46

举例：统计学科等级水平
根据考试成绩，统计学科等级水平。
分析：某中学对学生的附加科目进行能力测试，并按以下标准统计学科等级水平。
（1）生物和科学两门课都达到60分，总分达到180分为及格；
（2）每门课达到85分，总分达到260分为优秀；
（3）总分不到180分或有任意一门课不到60分，为不及格。
学生成绩原始数据如图所示。
编程要求：从用制表符(\t)分割的score.txt文件中读取学生成…', '7cc52f7fe524f07c73b2e1dd8488975065bbdc861bcddfce93c3524f45d1f3a1', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '举例：统计学科等级水平
根据考试成绩，统计学科等级水平。
分析：某中学对学生的附加科目进行能力测试，并按以下标准统计学科等级水平。
（1）生物和科学两门课都达到60分，总分达到180分为及格；
（2）每门课达到85分，总分达到260分为优秀；
（3）总分不到180分或有任意一门课不到60分，为不及格。
学生成绩原始数据如图所示。
编程要求：从用制表符(\t)分割的score.txt文件中读取学生成…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11123, 43, 191, 184, 47, 0, '（1）读取文件score.txt数据到列表L中：列表L中的数据项对应着文件中的每条学生记录，通过循环语句遍历L，提取需要的考号和三门课的成绩，并存放在列表x中。', '（1）读取文件score.txt数据到列表L中：列表L中的数据项对应着文件中的每条学生记录，通过循环语句遍历L，提取需要的考号和三门课的成绩，并存放在列表x中。

（1）读取文件score.txt数据到列表L中：列表L中的数据项对应着文件中的每条学生记录，通过循环语句遍历L，提取需要的考号和三门课的成绩，并存放在列表x中。
（2）判定学科等级：列表x包含4个数据项，x[0]为考号，x[1]、x[2]和x[3]分别为“程序设计”、“生物”和“科学”三门课的成绩，需要转换为整数类型以便进行求和等数值运算。最后通过分支语句，将求得的等级结果存放在key变量中。
（3）将考号和等级结果按一定格式写入文件level.txt中。
2025/4/1
数据存取与分析
47
举例：统计学科等级水平 - 解法一

（1）读取文件score.txt数据到列表L中：列表L中的数据项对应着文件中的每条学生记录，通过循环语句遍历L，提取需要的考号和三门课的成绩，并存放在列表x中。
（2）判定学科等级：列表x包含4个数据项，x[0]为考号，x[1]、x[2]和x[3]分别为“程序设计”、“生物”和“科学”三门课的成绩，需要转换为整数类型以便进行求和等数值运算。最后通过分支语句，将求得的等级结果存放在key变量中。
（…', '94d7bcd862ec32c40acc0e52655b15ea320a8baa9be74ef7d89edd19693c68f0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '（1）读取文件score.txt数据到列表L中：列表L中的数据项对应着文件中的每条学生记录，通过循环语句遍历L，提取需要的考号和三门课的成绩，并存放在列表x中。
（2）判定学科等级：列表x包含4个数据项，x[0]为考号，x[1]、x[2]和x[3]分别为“程序设计”、“生物”和“科学”三门课的成绩，需要转换为整数类型以便进行求和等数值运算。最后通过分支语句，将求得的等级结果存放在key变量中。
（…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11124, 43, 191, 184, 48, 0, '举例：统计学科等级水平 - 解法二', '举例：统计学科等级水平 - 解法二

举例：统计学科等级水平 - 解法二
解法一利用列表存放文件中的数据，需要占用额外的内存空间。更优的处理方法是使用readline()语句读取文件score.txt中的学生记录，对每条记录，判定该学生考核等级，并与考号合并写入文件level.txt中。若某次循环读到空行，则跳出循环，结束对文件的处理。
2025/4/1
数据存取与分析
48

举例：统计学科等级水平 - 解法二
解法一利用列表存放文件中的数据，需要占用额外的内存空间。更优的处理方法是使用readline()语句读取文件score.txt中的学生记录，对每条记录，判定该学生考核等级，并与考号合并写入文件level.txt中。若某次循环读到空行，则跳出循环，结束对文件的处理。
2025/4/1
数据存取与分析
48', 'ff1fe5061c7aaa7bb0201e9d5599bfc1fdd38a69d749c93ab40a78be3a6e3820', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '举例：统计学科等级水平 - 解法二
解法一利用列表存放文件中的数据，需要占用额外的内存空间。更优的处理方法是使用readline()语句读取文件score.txt中的学生记录，对每条记录，判定该学生考核等级，并与考号合并写入文件level.txt中。若某次循环读到空行，则跳出循环，结束对文件的处理。
2025/4/1
数据存取与分析
48') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11125, 43, 191, 184, 49, 0, '举例：统计某区域出现的车辆信息', '举例：统计某区域出现的车辆信息

举例：统计某区域出现的车辆信息
"car_data.txt"文件是以英文逗号分隔的数据文本文件（文件局部如图所示），记录了某出租汽车公司部分车辆某日0：00~23：00的车辆位置，无标题行。对应列分别是时间、车牌号、北纬、东经。
因需要协助查找该日发生在北纬31.2222-31.2333，东经121.45-121.55区域内的案件，编写程序，找到并打印位于该区域内该出租车公司的车辆信息
2025/4/1
数据存取与分析
49

举例：统计某区域出现的车辆信息
"car_data.txt"文件是以英文逗号分隔的数据文本文件（文件局部如图所示），记录了某出租汽车公司部分车辆某日0：00~23：00的车辆位置，无标题行。对应列分别是时间、车牌号、北纬、东经。
因需要协助查找该日发生在北纬31.2222-31.2333，东经121.45-121.55区域内的案件，编写程序，找到并打印位于该区域内该出租车公司的车辆信息
2025/…', '92d4cf66215265c620a5c99255ed4dd9ad98a41bc86b0889a2a6132956c19d56', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '举例：统计某区域出现的车辆信息
"car_data.txt"文件是以英文逗号分隔的数据文本文件（文件局部如图所示），记录了某出租汽车公司部分车辆某日0：00~23：00的车辆位置，无标题行。对应列分别是时间、车牌号、北纬、东经。
因需要协助查找该日发生在北纬31.2222-31.2333，东经121.45-121.55区域内的案件，编写程序，找到并打印位于该区域内该出租车公司的车辆信息
2025/…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11126, 43, 191, 184, 50, 0, '举例：统计某区域出现的车辆信息', '举例：统计某区域出现的车辆信息

举例：统计某区域出现的车辆信息
2025/4/1
数据存取与分析
50

举例：统计某区域出现的车辆信息
2025/4/1
数据存取与分析
50', '9334a71b178e6d6a511dd776146d764fee8f9e40b38228035f5508f95a20cf26', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第六章：数据存取与分析.pdf', '举例：统计某区域出现的车辆信息
2025/4/1
数据存取与分析
50') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11127, 43, 192, NULL, 1, 0, '计算思维与人工智能（Python 语言）', '计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）

计算思维与人工智能（Python 语言）', '0ee3618868489fd6f0bf19bc08385310460d97765940d75e9e3db944acaaa69d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '计算思维与人工智能（Python 语言）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11128, 43, 192, NULL, 2, 0, '第七章：归纳与抽象', '第七章：归纳与抽象

第七章：归纳与抽象

第七章：归纳与抽象', '634d2dc50ff9b9bbee7d05ca7230c42c96c56b698fdede454e8f76d54923011d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第七章：归纳与抽象') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11129, 43, 192, NULL, 3, 0, '引言', '引言

引言
在计算思维的世界中，解决问题的关键在于如何有效地归纳和抽象
无论是简单的日常问题还是复杂的编程任务，我们都需要将问题分解为更小的部分，并通过归纳出一般规律来设计解决方案。
本章将引导学生深入理解这两种思维方式如何通过编程语言具体化为强大的工具——函数与面向对象编程。
2024/11/18
归纳与抽象
3

引言
在计算思维的世界中，解决问题的关键在于如何有效地归纳和抽象
无论是简单的日常问题还是复杂的编程任务，我们都需要将问题分解为更小的部分，并通过归纳出一般规律来设计解决方案。
本章将引导学生深入理解这两种思维方式如何通过编程语言具体化为强大的工具——函数与面向对象编程。
2024/11/18
归纳与抽象
3', '4d1cc98d15915107e3be26a3c77eb93c424c9a0ddaf538bec123ae05552347fd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '引言
在计算思维的世界中，解决问题的关键在于如何有效地归纳和抽象
无论是简单的日常问题还是复杂的编程任务，我们都需要将问题分解为更小的部分，并通过归纳出一般规律来设计解决方案。
本章将引导学生深入理解这两种思维方式如何通过编程语言具体化为强大的工具——函数与面向对象编程。
2024/11/18
归纳与抽象
3') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11130, 43, 192, NULL, 4, 0, '引言', '引言

引言
函数，了解如何通过函数将复杂的程序逻辑分解为独立的语句块，使代码更易于理解和维护。
类和对象，学习如何利用类和对象来模拟现实世界的问题，并通过封装、继承与多态，创建更灵活和可扩展的程序
模块化编程，讨论如何将函数和面向对象编程结合起来，设计出结构合理且易于管理的复杂程序
通过本章的学习，将不再仅仅停留在具体的编程语法上，而是能够站在计算思维的高度，理解编程背后的逻辑与哲学
2024/11/18
归纳与抽象
4

引言
函数，了解如何通过函数将复杂的程序逻辑分解为独立的语句块，使代码更易于理解和维护。
类和对象，学习如何利用类和对象来模拟现实世界的问题，并通过封装、继承与多态，创建更灵活和可扩展的程序
模块化编程，讨论如何将函数和面向对象编程结合起来，设计出结构合理且易于管理的复杂程序
通过本章的学习，将不再仅仅停留在具体的编程语法上，而是能够站在计算思维的高度，理解编程背后的逻辑与哲学
2024/11/1…', '33a70628512a2cfb36d58b12238d9f53d6d0af3a8caf31f01f188792b3fa7538', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '引言
函数，了解如何通过函数将复杂的程序逻辑分解为独立的语句块，使代码更易于理解和维护。
类和对象，学习如何利用类和对象来模拟现实世界的问题，并通过封装、继承与多态，创建更灵活和可扩展的程序
模块化编程，讨论如何将函数和面向对象编程结合起来，设计出结构合理且易于管理的复杂程序
通过本章的学习，将不再仅仅停留在具体的编程语法上，而是能够站在计算思维的高度，理解编程背后的逻辑与哲学
2024/11/1…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11131, 43, 192, NULL, 5, 0, '目录', '目录

目录
7.1、函数
7.2、类和对象
7.3、模块化编程
2024/11/18
归纳与抽象
5

目录
7.1、函数
7.2、类和对象
7.3、模块化编程
2024/11/18
归纳与抽象
5', '7c9870e3679553478e251981101f6489d43eb037c09dc33d4987a236c0b32439', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '目录
7.1、函数
7.2、类和对象
7.3、模块化编程
2024/11/18
归纳与抽象
5') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11132, 43, 192, 185, 6, 0, '7.1、函数', '7.1、函数

7.1、函数
2024/11/18
归纳与抽象
6

7.1、函数
2024/11/18
归纳与抽象
6', '45e46af2bd4d89b527de191f31032799d238cfd0587d29b90215a6c4078c2ce2', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.1、函数
2024/11/18
归纳与抽象
6') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11133, 43, 192, 185, 7, 0, '7.1.1、函数的定义和调用', '7.1.1、函数的定义和调用

7.1.1、函数的定义和调用
2024/11/18
归纳与抽象
7

7.1.1、函数的定义和调用
2024/11/18
归纳与抽象
7', '0e623b815db02c2f934a8fefc2bf7aada4d3e39a788b8bf56df839185e61495b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.1.1、函数的定义和调用
2024/11/18
归纳与抽象
7') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11134, 43, 192, NULL, 8, 0, '从一段唱歌代码开始…', '从一段唱歌代码开始…

从一段唱歌代码开始…
2024/11/18
归纳与抽象
8
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")

从一段唱歌代码开始…
2024/11/18
归纳与抽象
8
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("…', 'f9c30166ed5094de677e4c960d236135579e9f506bfa47a84c7c8f69e6f5aa10', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '从一段唱歌代码开始…
2024/11/18
归纳与抽象
8
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11135, 43, 192, NULL, 9, 0, '单曲循环10遍', '单曲循环10遍

单曲循环10遍
2024/11/18
归纳与抽象
9
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")
for i in range(10):

单曲循环10遍
2024/11/18
归纳与抽象
9
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你…', '9804fc5ffd52f37698f0d9f26c63f93c39b6d157047f5ec641940526f363ac0f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '单曲循环10遍
2024/11/18
归纳与抽象
9
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11136, 43, 192, NULL, 10, 0, '周杰伦，不止一首歌…', '周杰伦，不止一首歌…

周杰伦，不止一首歌…
2024/11/18
归纳与抽象
10
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")
#演唱《稻香》片段
print("=======稻香==begin========")
print("对这个世界如果你有太多的抱怨")
print("跌倒了 就不敢继续往前走")
print("为什么 人要这么的脆弱 堕落")
print("请你打开电视看看 多少人")
print("为生命在努力勇敢的走下去")
print("我们是不是该知足")
print("...")
print("=======稻香==end=======")
print("")
简单爱
稻香
稻香
简单爱
12行
10行

周杰伦，不止一首歌…
2024/11/18
归纳与抽象
10
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print(…', 'd0b2b4c02060456be540dd5c298c2fcca2ce7eae8d796f6d77ecd799b654dc96', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '周杰伦，不止一首歌…
2024/11/18
归纳与抽象
10
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print(…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11137, 43, 192, NULL, 11, 0, '重用代码', '重用代码

重用代码
2024/11/18
归纳与抽象
11
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")
#演唱《稻香》片段
print("=======稻香==begin========")
print("对这个世界如果你有太多的抱怨")
print("跌倒了 就不敢继续往前走")
print("为什么 人要这么的脆弱 堕落")
print("请你打开电视看看 多少人")
print("为生命在努力勇敢的走下去")
print("我们是不是该知足")
print("...")
print("=======稻香==end=======")
print("")
A
B
A
B
B
A
类似字典的映射关系
用A来代替演唱《简单爱》代码片段
用B来代替演唱《稻香》代码片段

重用代码
2024/11/18
归纳与抽象
11
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手…', '52db6bf8e8b05af3c8432777f406c82941cf3fb6d802ac692ef0847e6696b276', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '重用代码
2024/11/18
归纳与抽象
11
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11138, 43, 192, NULL, 12, 0, '特定功能', '特定功能

特定功能
2024/11/18
归纳与抽象
12
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")

#演唱《稻香》片段
print("=======稻香==begin========")
print("对这个世界如果你有太多的抱怨")
print("跌倒了 就不敢继续往前走")
print("为什么 人要这么的脆弱 堕落")
print("请你打开电视看看 多少人")
print("为生命在努力勇敢的走下去")
print("我们是不是该知足")
print("...")
print("=======稻香==end=======")
print("")
A
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手 一阵莫名感动")
print("我想带你 回我的外婆家")
print("一起看着日落 一直到我们都睡着")
print("...")
print("=======简单爱==end=======")
print("")
#演唱《稻香》片段
print("=======稻香==begin========")
print("对这个世界如果你有太多的抱怨")
print("跌倒了 就不敢继续往前走")
print("为什么 人要这么的脆弱 堕落")
print("请你打开电视看看 多少人")
print("为生命在努力勇敢的走下去")
print("我们是不是该知足")
print("...")
print("=======稻香==end=======")
print("")
A
B
也可以用A来代替演唱《简单爱》和《稻香》代码片段
但没有分别表示好，为什么？
A
A
A
B
A
B

特定功能
2024/11/18
归纳与抽象
12
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手…', '89931e1ce44c19924766dcd17478f7bf8638b779f7ec7e8fa2ae44f3378f9677', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '特定功能
2024/11/18
归纳与抽象
12
#演唱《简单爱》片段
print("=======简单爱==begin========")
print("说不上为什麽 我变得很主动")
print("若爱上一个人 什麽都会值得去做")
print("我想大声宣布 对你依依不舍")
print("连隔壁邻居都猜到我现在的感受")
print("河边的风 在吹着头发飘动")
print("牵着你的手…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11139, 43, 192, 185, 13, 0, '认识函数', '认识函数

认识函数
函数是一段具有特定功能的、可重用的语句组
调用内置库和第三方库时，都在使用函数
Random
random.random()
random.randint(10)
2024/11/18
归纳与抽象
13

认识函数
函数是一段具有特定功能的、可重用的语句组
调用内置库和第三方库时，都在使用函数
Random
random.random()
random.randint(10)
2024/11/18
归纳与抽象
13', 'e90366b579cec82191142ffc73454362e88ffebaaf771f8eec94cf22c21da569', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '认识函数
函数是一段具有特定功能的、可重用的语句组
调用内置库和第三方库时，都在使用函数
Random
random.random()
random.randint(10)
2024/11/18
归纳与抽象
13') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11140, 43, 192, 185, 14, 0, '函数的定义', '函数的定义

函数的定义
2024/11/18
归纳与抽象
14
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>

函数的定义
2024/11/18
归纳与抽象
14
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>', '6767c80082b2d6fa96010a28b78feec63b8be9d250f7ff7a05025df5d476b656', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数的定义
2024/11/18
归纳与抽象
14
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11141, 43, 192, 185, 15, 0, '函数的调用', '函数的调用

函数的调用
2024/11/18
归纳与抽象
15
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>
<参数列表>
<返回值列表>
<参数列表>
<返回值列表>
主程序
函数
函数

函数的调用
2024/11/18
归纳与抽象
15
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>
<参数列表>
<返回值列表>
<参数列表>
<返回值列表>
主程序
函数
函数', 'bc860db1298bc4f24da19efd51553458bda7d25638779952d319af5802c8035d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数的调用
2024/11/18
归纳与抽象
15
Python定义一个函数使用def保留字，语法形式如下：
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>
<参数列表>
<返回值列表>
<参数列表>
<返回值列表>
主程序
函数
函数') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11142, 43, 192, 185, 16, 0, '第一种函数：无参数，无返回值', '第一种函数：无参数，无返回值

第一种函数：无参数，无返回值
2024/11/18
归纳与抽象
16
def <函数名>():
   <函数体>

第一种函数：无参数，无返回值
2024/11/18
归纳与抽象
16
def <函数名>():
   <函数体>', 'bdc9b99fcde2a73c3343451f8142d74b00ffe806ad70a3eea87a2db91fe9ae91', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第一种函数：无参数，无返回值
2024/11/18
归纳与抽象
16
def <函数名>():
   <函数体>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11143, 43, 192, 185, 17, 0, '举例：第一种函数定义', '举例：第一种函数定义

举例：第一种函数定义
2024/11/18
归纳与抽象
17
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
    print("连隔壁邻居都猜到我现在的感受")
    print("河边的风 在吹着头发飘动")
    print("牵着你的手 一阵莫名感动")
    print("我想带你 回我的外婆家")
    print("一起看着日落 一直到我们都睡着")
    print("...")
    print("=======简单爱==end=======")
    print("")
def sing_daoxiang():
    #一个函数，实现演唱《稻香》片段
    print("=======稻香==begin========")
    print("对这个世界如果你有太多的抱怨")
    print("跌倒了 就不敢继续往前走")
    print("为什么 人要这么的脆弱 堕落")
    print("请你打开电视看看 多少人")
    print("为生命在努力勇敢的走下去")
    print("我们是不是该知足")
    print("...")
    print("=======稻香==end=======")
    print("")

举例：第一种函数定义
2024/11/18
归纳与抽象
17
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
   …', '20b85b6ccb7f72f226ca45997fe8bb97c760cbb6ef17a3279a7cb4b30439b316', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：第一种函数定义
2024/11/18
归纳与抽象
17
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
   …') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11144, 43, 192, 185, 18, 0, '举例：第一种函数调用', '举例：第一种函数调用

举例：第一种函数调用
函数名+（）
2024/11/18
归纳与抽象
18
sing_jiandanai()
sing_daoxiang()
sing_jiandanai()
sing_jiandanai()
sing_jiandanai()

举例：第一种函数调用
函数名+（）
2024/11/18
归纳与抽象
18
sing_jiandanai()
sing_daoxiang()
sing_jiandanai()
sing_jiandanai()
sing_jiandanai()', '88c08665d631b8b48c19d061e10dc93537aaea87e43bc71cca0cd68d02651e4f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：第一种函数调用
函数名+（）
2024/11/18
归纳与抽象
18
sing_jiandanai()
sing_daoxiang()
sing_jiandanai()
sing_jiandanai()
sing_jiandanai()') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11145, 43, 192, 185, 19, 0, '第二种函数：有参数，无返回值', '第二种函数：有参数，无返回值

第二种函数：有参数，无返回值
2024/11/18
归纳与抽象
19
def <函数名>(<参数列表>):
   <函数体>

第二种函数：有参数，无返回值
2024/11/18
归纳与抽象
19
def <函数名>(<参数列表>):
   <函数体>', '280d16cf85160f42fc601e39c55b43b5637b69d3dbf1377620b5b677a2b61fc7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第二种函数：有参数，无返回值
2024/11/18
归纳与抽象
19
def <函数名>(<参数列表>):
   <函数体>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11146, 43, 192, 185, 20, 0, '生日歌。', '生日歌。

生日歌。
过生日时要为朋友唱生日歌，
歌词为：
Happy birthday to you!
Happy birthday to you!
Happy birthday, dear <名字>
Happy birthday to you!
2024/11/18
归纳与抽象
20
举例：第二种函数定义
def sing_happybirthday(name):
    print("Happy birthday to you!")
    print("Happy birthday to you!")
    print("Happy birthday, dear {}!".format(name))
    print("Happy birthday to you!")

生日歌。
过生日时要为朋友唱生日歌，
歌词为：
Happy birthday to you!
Happy birthday to you!
Happy birthday, dear <名字>
Happy birthday to you!
2024/11/18
归纳与抽象
20
举例：第二种函数定义
def sing_happybirthday(name):
    print("Happy bir…', '26db037b5d351b52add524418b88e9a32ae20440e099d5b98bb86c022f9d386b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '生日歌。
过生日时要为朋友唱生日歌，
歌词为：
Happy birthday to you!
Happy birthday to you!
Happy birthday, dear <名字>
Happy birthday to you!
2024/11/18
归纳与抽象
20
举例：第二种函数定义
def sing_happybirthday(name):
    print("Happy bir…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11147, 43, 192, 185, 21, 0, '函数名+（参数列表）', '函数名+（参数列表）

函数名+（参数列表）
参数列表形式
值
sing_happybirthday("Jay Chou")
参数
aa = “Jay Chou”
sing_happybirthday(aa)
2024/11/18
归纳与抽象
21
举例：第二种函数调用

函数名+（参数列表）
参数列表形式
值
sing_happybirthday("Jay Chou")
参数
aa = “Jay Chou”
sing_happybirthday(aa)
2024/11/18
归纳与抽象
21
举例：第二种函数调用', '65e264506867099028bc4f16a95ffb16220f81d160637a4b2b089e458bac2a9f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数名+（参数列表）
参数列表形式
值
sing_happybirthday("Jay Chou")
参数
aa = “Jay Chou”
sing_happybirthday(aa)
2024/11/18
归纳与抽象
21
举例：第二种函数调用') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11148, 43, 192, 185, 22, 0, '举例：两个参数函数定义及调用', '举例：两个参数函数定义及调用

举例：两个参数函数定义及调用
2024/11/18
归纳与抽象
22

举例：两个参数函数定义及调用
2024/11/18
归纳与抽象
22', '363505e4ae7106ff1bdce303ba8116fcab4e7996e6cb01624d75e849b9903525', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：两个参数函数定义及调用
2024/11/18
归纳与抽象
22') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11149, 43, 192, 185, 23, 0, '第三种函数：无参数，有返回值', '第三种函数：无参数，有返回值

第三种函数：无参数，有返回值
2024/11/18
归纳与抽象
23
def <函数名>():
   <函数体>
   return <返回值列表>

第三种函数：无参数，有返回值
2024/11/18
归纳与抽象
23
def <函数名>():
   <函数体>
   return <返回值列表>', '657c054527817086b9b9c36a50dee221dd474c0e50566e1b00c737ee4f095cae', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三种函数：无参数，有返回值
2024/11/18
归纳与抽象
23
def <函数名>():
   <函数体>
   return <返回值列表>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11150, 43, 192, 185, 24, 0, '举例：第三种函数定义及调用', '举例：第三种函数定义及调用

举例：第三种函数定义及调用
接收变量 = 函数名+（）
2024/11/18
归纳与抽象
24

举例：第三种函数定义及调用
接收变量 = 函数名+（）
2024/11/18
归纳与抽象
24', '2de8227aee0aecd5ced56987fa966c5ef5f37588205e2409fc365e3ef6cd036c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：第三种函数定义及调用
接收变量 = 函数名+（）
2024/11/18
归纳与抽象
24') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11151, 43, 192, 185, 25, 0, '举例：多个返回值的函数定义及调用', '举例：多个返回值的函数定义及调用

举例：多个返回值的函数定义及调用
2024/11/18
归纳与抽象
25

举例：多个返回值的函数定义及调用
2024/11/18
归纳与抽象
25', 'de3f1217fefac0f785610cde767c126a2d5010ed3064d524b81b65ed21a5f3d9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：多个返回值的函数定义及调用
2024/11/18
归纳与抽象
25') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11152, 43, 192, 185, 26, 0, '第四种函数：有参数，有返回值', '第四种函数：有参数，有返回值

第四种函数：有参数，有返回值
2024/11/18
归纳与抽象
26
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>

第四种函数：有参数，有返回值
2024/11/18
归纳与抽象
26
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>', '8a5b04929a3f3ce24c024829bf614c766db4945c3412e72154ca7ca939c57d46', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第四种函数：有参数，有返回值
2024/11/18
归纳与抽象
26
def <函数名>(<参数列表>):
   <函数体>
   return <返回值列表>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11153, 43, 192, 185, 27, 0, '函数定义', '函数定义

函数定义
2024/11/18
归纳与抽象
27
def <函数名>():
    <函数体>
    return None
def <函数名>():
    <函数体>
    return 返回值
def <函数名>(参数列表):
    <函数体>
    return None
def <函数名>(参数列表):
    <函数体>
    return 返回值

函数定义
2024/11/18
归纳与抽象
27
def <函数名>():
    <函数体>
    return None
def <函数名>():
    <函数体>
    return 返回值
def <函数名>(参数列表):
    <函数体>
    return None
def <函数名>(参数列表):
    <函数体>
    return 返回值', 'fae40f6828a859de61bf4fbc5437f2725567d8dfbfbb9e0c1966845409f80eaa', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数定义
2024/11/18
归纳与抽象
27
def <函数名>():
    <函数体>
    return None
def <函数名>():
    <函数体>
    return 返回值
def <函数名>(参数列表):
    <函数体>
    return None
def <函数名>(参数列表):
    <函数体>
    return 返回值') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11154, 43, 192, 185, 28, 0, '函数无返回值', '函数无返回值

函数无返回值
函数无参数
<函数名>()
函数有参数
<函数名>(参数列表)
函数有返回值
函数无参数
接收变量 = <函数名>()
函数有参数
接收变量 = <函数名>(参数列表)
2024/11/18
归纳与抽象
28
函数调用

函数无返回值
函数无参数
<函数名>()
函数有参数
<函数名>(参数列表)
函数有返回值
函数无参数
接收变量 = <函数名>()
函数有参数
接收变量 = <函数名>(参数列表)
2024/11/18
归纳与抽象
28
函数调用', 'b009d89ec28bdb018d67fb6daf4e01dc1a66db909493815b3a07c4dcde1b607d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数无返回值
函数无参数
<函数名>()
函数有参数
<函数名>(参数列表)
函数有返回值
函数无参数
接收变量 = <函数名>()
函数有参数
接收变量 = <函数名>(参数列表)
2024/11/18
归纳与抽象
28
函数调用') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11155, 43, 192, 185, 29, 0, '函数变量的作用域', '函数变量的作用域

函数变量的作用域
在Python中，变量的作用域（Scope）决定了变量在程序中可以被访问的范围
Python中的变量作用域通常分为以下几类：
局部作用域（Local Scope）
全局作用域（Global Scope）
2024/11/18
归纳与抽象
29

函数变量的作用域
在Python中，变量的作用域（Scope）决定了变量在程序中可以被访问的范围
Python中的变量作用域通常分为以下几类：
局部作用域（Local Scope）
全局作用域（Global Scope）
2024/11/18
归纳与抽象
29', '878ce89566ae97e029921f4314925595c21c0fe97b4b1c77de16290c9da4daf9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数变量的作用域
在Python中，变量的作用域（Scope）决定了变量在程序中可以被访问的范围
Python中的变量作用域通常分为以下几类：
局部作用域（Local Scope）
全局作用域（Global Scope）
2024/11/18
归纳与抽象
29') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11156, 43, 192, 185, 30, 0, '局部作用域', '局部作用域

局部作用域
局部作用域是指在函数内部定义的变量，它们只能在该函数内部被访问。
当函数执行完毕后，这些局部变量就会被销毁，无法在函数外部访问。
为什么使用局部变量？
局部变量的主要作用是防止函数内部的变量意外影响其他部分的代码，确保函数的内部状态是独立的。这使得代码更容易维护和理解。
2024/11/18
归纳与抽象
30

局部作用域
局部作用域是指在函数内部定义的变量，它们只能在该函数内部被访问。
当函数执行完毕后，这些局部变量就会被销毁，无法在函数外部访问。
为什么使用局部变量？
局部变量的主要作用是防止函数内部的变量意外影响其他部分的代码，确保函数的内部状态是独立的。这使得代码更容易维护和理解。
2024/11/18
归纳与抽象
30', 'e5ee21b8320e97e4526b86cd00c32b5176391ae4facc06a11ad8f4a96b641bb3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '局部作用域
局部作用域是指在函数内部定义的变量，它们只能在该函数内部被访问。
当函数执行完毕后，这些局部变量就会被销毁，无法在函数外部访问。
为什么使用局部变量？
局部变量的主要作用是防止函数内部的变量意外影响其他部分的代码，确保函数的内部状态是独立的。这使得代码更容易维护和理解。
2024/11/18
归纳与抽象
30') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11157, 43, 192, 185, 31, 0, '局部作用域', '局部作用域

局部作用域
2024/11/18
归纳与抽象
31

局部作用域
2024/11/18
归纳与抽象
31', 'c03e7c26c4f43084fa772de5a3647a53f10c3d7f4ee4a922e5d50bb851eca290', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '局部作用域
2024/11/18
归纳与抽象
31') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11158, 43, 192, 185, 32, 0, '全局作用域', '全局作用域

全局作用域
全局作用域中的变量是在函数外部定义的，它们可以在程序的任何地方被访问。
如果需要在函数内部修改全局变量的值，必须使用 global 关键字。
2024/11/18
归纳与抽象
32

全局作用域
全局作用域中的变量是在函数外部定义的，它们可以在程序的任何地方被访问。
如果需要在函数内部修改全局变量的值，必须使用 global 关键字。
2024/11/18
归纳与抽象
32', 'bb12ba9b2effaa1d014631eaea429e373cebb2bb1877bcc0156a687987e2acb8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '全局作用域
全局作用域中的变量是在函数外部定义的，它们可以在程序的任何地方被访问。
如果需要在函数内部修改全局变量的值，必须使用 global 关键字。
2024/11/18
归纳与抽象
32') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11159, 43, 192, 185, 33, 0, '示例：全局变量', '示例：全局变量

示例：全局变量
2024/11/18
归纳与抽象
33
要在函数内部修改全局变量的值，必须显式地使用 global 关键字。

示例：全局变量
2024/11/18
归纳与抽象
33
要在函数内部修改全局变量的值，必须显式地使用 global 关键字。', 'adc12ff4fe58fad2e12e661ac90230f12402f45eab708e96cddf075150bbafbd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '示例：全局变量
2024/11/18
归纳与抽象
33
要在函数内部修改全局变量的值，必须显式地使用 global 关键字。') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11160, 43, 192, 185, 34, 0, '局部变量与全局变量的冲突', '局部变量与全局变量的冲突

局部变量与全局变量的冲突
如果局部变量和全局变量同名，函数内部会优先使用局部变量。这种情况下，函数内部的局部变量会“遮蔽”全局变量。
2024/11/18
归纳与抽象
34

局部变量与全局变量的冲突
如果局部变量和全局变量同名，函数内部会优先使用局部变量。这种情况下，函数内部的局部变量会“遮蔽”全局变量。
2024/11/18
归纳与抽象
34', '2780dde410677869e962e5bebc5eac38fb0547ffe4e468e7eac3ee5e234a7fa0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '局部变量与全局变量的冲突
如果局部变量和全局变量同名，函数内部会优先使用局部变量。这种情况下，函数内部的局部变量会“遮蔽”全局变量。
2024/11/18
归纳与抽象
34') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11161, 43, 192, NULL, 35, 0, '举例：几何形状的面积', '举例：几何形状的面积

举例：几何形状的面积
将每种形状的面积计算逻辑封装在各自的函数中。
2024/11/18
归纳与抽象
35

举例：几何形状的面积
将每种形状的面积计算逻辑封装在各自的函数中。
2024/11/18
归纳与抽象
35', '08fd183c59a77f4839250d3e3b911cbe7eea1fcd542720c8d78d3cf60a979a06', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：几何形状的面积
将每种形状的面积计算逻辑封装在各自的函数中。
2024/11/18
归纳与抽象
35') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11162, 43, 192, NULL, 36, 0, '举例：电子商务系统', '举例：电子商务系统

举例：电子商务系统
在一个简单的电子商务系统中，我们可以创建独立的模块来处理商品的管理、购物车的操作和订单的生成
2024/11/18
归纳与抽象
36

举例：电子商务系统
在一个简单的电子商务系统中，我们可以创建独立的模块来处理商品的管理、购物车的操作和订单的生成
2024/11/18
归纳与抽象
36', 'de485534806167cb750957e0c406e636d30d591a3be7d7d98120b0663b4c81f7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：电子商务系统
在一个简单的电子商务系统中，我们可以创建独立的模块来处理商品的管理、购物车的操作和订单的生成
2024/11/18
归纳与抽象
36') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11163, 43, 192, NULL, 37, 0, '举例：用户管理系统', '举例：用户管理系统

举例：用户管理系统
2024/11/18
归纳与抽象
37

举例：用户管理系统
2024/11/18
归纳与抽象
37', 'e3a4dd568e3e3fab1237a4ea43ec9b707c1bed85102b1ec8defef90f532540c5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：用户管理系统
2024/11/18
归纳与抽象
37') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11164, 43, 192, NULL, 38, 0, '7.1.2、 典型函数介绍', '7.1.2、 典型函数介绍

7.1.2、 典型函数介绍
2024/11/18
归纳与抽象
38

7.1.2、 典型函数介绍
2024/11/18
归纳与抽象
38', '3e5997c9507d94028eb68cf49f93479d0ab871c14b3697f4cde5bf29f66e47dc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.1.2、 典型函数介绍
2024/11/18
归纳与抽象
38') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11165, 43, 192, NULL, 39, 0, '典型函数介绍', '典型函数介绍

典型函数介绍
input
print
max
2024/11/18
归纳与抽象
39

典型函数介绍
input
print
max
2024/11/18
归纳与抽象
39', '3bfa693698953d7aa06bd114de81c756a6dec8ca6eaf372649e67d59ca35ed4d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '典型函数介绍
input
print
max
2024/11/18
归纳与抽象
39') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11166, 43, 192, 185, 40, 0, 'lambda函数', 'lambda函数

lambda函数
Python的有33个保留字，其中一个是lambda，该保留字用于定义一种特殊的函数——匿名函数，又称lambda函数。
匿名函数并非没有名字，而是将函数名作为函数结果返回，如下：
<函数名> = lambda <参数列表>: <表达式>
lambda函数与正常函数一样，等价于下面形式：
def <函数名>(<参数列表>):
     return <表达式>
2024/11/18
归纳与抽象
40

lambda函数
Python的有33个保留字，其中一个是lambda，该保留字用于定义一种特殊的函数——匿名函数，又称lambda函数。
匿名函数并非没有名字，而是将函数名作为函数结果返回，如下：
<函数名> = lambda <参数列表>: <表达式>
lambda函数与正常函数一样，等价于下面形式：
def <函数名>(<参数列表>):
     return <表达式>
2024/11/1…', 'f27ab6cc1f942224e74d156f981ee0d96bcc212bc6e20afe33b29c5b68344d71', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'lambda函数
Python的有33个保留字，其中一个是lambda，该保留字用于定义一种特殊的函数——匿名函数，又称lambda函数。
匿名函数并非没有名字，而是将函数名作为函数结果返回，如下：
<函数名> = lambda <参数列表>: <表达式>
lambda函数与正常函数一样，等价于下面形式：
def <函数名>(<参数列表>):
     return <表达式>
2024/11/1…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11167, 43, 192, 185, 41, 0, 'lambda函数', 'lambda函数

lambda函数
简单说，lambda函数用于定义简单的、能够在一行内表示的函数，返回一个函数类型，实例如下。
2024/11/18
归纳与抽象
41
def f(x,y):
    return x + y

lambda函数
简单说，lambda函数用于定义简单的、能够在一行内表示的函数，返回一个函数类型，实例如下。
2024/11/18
归纳与抽象
41
def f(x,y):
    return x + y', '57b4002f3bef562e42fcf8cafdc42aff5b9f684907b241d4d93c4ea2e199b021', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'lambda函数
简单说，lambda函数用于定义简单的、能够在一行内表示的函数，返回一个函数类型，实例如下。
2024/11/18
归纳与抽象
41
def f(x,y):
    return x + y') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11168, 43, 192, 185, 42, 0, '字典排序中的lambda', '字典排序中的lambda

字典排序中的lambda
2024/11/18
归纳与抽象
42

字典排序中的lambda
2024/11/18
归纳与抽象
42', '92a85d77d5010b4eac7b55c6569f846cac732d029b50a3bd82e6fb0210ef2f26', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '字典排序中的lambda
2024/11/18
归纳与抽象
42') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11169, 43, 192, NULL, 43, 0, '7.1.3、 函数的高级用法', '7.1.3、 函数的高级用法

7.1.3、 函数的高级用法
2024/11/18
归纳与抽象
43

7.1.3、 函数的高级用法
2024/11/18
归纳与抽象
43', 'faa197ceb9d6ecf1bcc0235684b13f43ffe86a9a29ef86c69bdab18ae577be5b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.1.3、 函数的高级用法
2024/11/18
归纳与抽象
43') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11170, 43, 192, 185, 44, 0, '带默认参数的函数', '带默认参数的函数

带默认参数的函数
在定义函数时，有些参数可以存在默认值
2024/11/18
归纳与抽象
44

带默认参数的函数
在定义函数时，有些参数可以存在默认值
2024/11/18
归纳与抽象
44', 'c5936470ad5efb2a48b6185693052c5375e1c03d27744a7063dc4a2b9bf0a269', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '带默认参数的函数
在定义函数时，有些参数可以存在默认值
2024/11/18
归纳与抽象
44') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11171, 43, 192, 185, 45, 0, '带可变数量参数的函数', '带可变数量参数的函数

带可变数量参数的函数
通常情况下，函数的参数是固定的，也就是说，函数定义时会明确指定接收几个参数。
然而，在许多情况下，我们可能希望函数能够处理不定数量的输入。这时，Python提供了两种特殊的语法来实现这一功能：
可变长元组参数 (*args)：用于接收不定数量的位置参数。
可变长字典参数 (**kwargs)：用于接收不定数量的关键字参数。
2024/11/18
归纳与抽象
45

带可变数量参数的函数
通常情况下，函数的参数是固定的，也就是说，函数定义时会明确指定接收几个参数。
然而，在许多情况下，我们可能希望函数能够处理不定数量的输入。这时，Python提供了两种特殊的语法来实现这一功能：
可变长元组参数 (*args)：用于接收不定数量的位置参数。
可变长字典参数 (**kwargs)：用于接收不定数量的关键字参数。
2024/11/18
归纳与抽象
45', '1fcb9abc9ca5c2ef8a7d93acb5359e51933aba9be0dd36bacba6f0da84f8505d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '带可变数量参数的函数
通常情况下，函数的参数是固定的，也就是说，函数定义时会明确指定接收几个参数。
然而，在许多情况下，我们可能希望函数能够处理不定数量的输入。这时，Python提供了两种特殊的语法来实现这一功能：
可变长元组参数 (*args)：用于接收不定数量的位置参数。
可变长字典参数 (**kwargs)：用于接收不定数量的关键字参数。
2024/11/18
归纳与抽象
45') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11172, 43, 192, 185, 46, 0, '可变长元组参数 (*args)', '可变长元组参数 (*args)

可变长元组参数 (*args)
基本概念
*args 允许函数接收任意数量的位置参数，并将它们存储在一个元组中。
通过使用 *args，你可以在调用函数时传入任意数量的参数，这些参数会按顺序保存在 args 元组中。
2024/11/18
归纳与抽象
46

可变长元组参数 (*args)
基本概念
*args 允许函数接收任意数量的位置参数，并将它们存储在一个元组中。
通过使用 *args，你可以在调用函数时传入任意数量的参数，这些参数会按顺序保存在 args 元组中。
2024/11/18
归纳与抽象
46', 'f7385154367b3ca67c0be4193b38add476cc7bca0c221d3116c2d4b17ab55471', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '可变长元组参数 (*args)
基本概念
*args 允许函数接收任意数量的位置参数，并将它们存储在一个元组中。
通过使用 *args，你可以在调用函数时传入任意数量的参数，这些参数会按顺序保存在 args 元组中。
2024/11/18
归纳与抽象
46') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11173, 43, 192, 185, 47, 0, '可变长元组参数 (*args)', '可变长元组参数 (*args)

可变长元组参数 (*args)
示例：定义一个接收任意数量数字并求和的函数：




应用场景：
当你需要编写一个函数，但不确定调用时会传入多少参数，比如对一组数据求和、打印多个值等。
2024/11/18
归纳与抽象
47

可变长元组参数 (*args)
示例：定义一个接收任意数量数字并求和的函数：




应用场景：
当你需要编写一个函数，但不确定调用时会传入多少参数，比如对一组数据求和、打印多个值等。
2024/11/18
归纳与抽象
47', 'ed72e024aa2f351737ea0f806a94704dced98e93099154b3199ff08fe3dc220d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '可变长元组参数 (*args)
示例：定义一个接收任意数量数字并求和的函数：




应用场景：
当你需要编写一个函数，但不确定调用时会传入多少参数，比如对一组数据求和、打印多个值等。
2024/11/18
归纳与抽象
47') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11174, 43, 192, 185, 48, 0, '可变长字典参数 (**kwargs)', '可变长字典参数 (**kwargs)

可变长字典参数 (**kwargs)
基本概念
**kwargs 允许函数接收任意数量的关键字参数，并将它们存储在一个字典中。
kwargs 也是习惯命名，实际使用中可以用其他名字代替。
2024/11/18
归纳与抽象
48

可变长字典参数 (**kwargs)
基本概念
**kwargs 允许函数接收任意数量的关键字参数，并将它们存储在一个字典中。
kwargs 也是习惯命名，实际使用中可以用其他名字代替。
2024/11/18
归纳与抽象
48', '9d627855d3de7e3b8c296785566960a1a50877716ac34adeb048eab08ed8bbf3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '可变长字典参数 (**kwargs)
基本概念
**kwargs 允许函数接收任意数量的关键字参数，并将它们存储在一个字典中。
kwargs 也是习惯命名，实际使用中可以用其他名字代替。
2024/11/18
归纳与抽象
48') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11175, 43, 192, 185, 49, 0, '可变长字典参数 (**kwargs)', '可变长字典参数 (**kwargs)

可变长字典参数 (**kwargs)
示例：定义一个接收任意数量关键字参数并打印的函数






应用场景：
当你需要接收和处理一组参数，这些参数在数量和名称上可能会有所不同，比如构建配置选项、处理用户输入等。
2024/11/18
归纳与抽象
49

可变长字典参数 (**kwargs)
示例：定义一个接收任意数量关键字参数并打印的函数






应用场景：
当你需要接收和处理一组参数，这些参数在数量和名称上可能会有所不同，比如构建配置选项、处理用户输入等。
2024/11/18
归纳与抽象
49', '97c4bb7ae8d2c10c3a678739cf5ed92b98683a24533086e37aafbdab5180a4c0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '可变长字典参数 (**kwargs)
示例：定义一个接收任意数量关键字参数并打印的函数






应用场景：
当你需要接收和处理一组参数，这些参数在数量和名称上可能会有所不同，比如构建配置选项、处理用户输入等。
2024/11/18
归纳与抽象
49') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11176, 43, 192, 185, 50, 0, '可变长字典参数传递方法', '可变长字典参数传递方法

可变长字典参数传递方法
2024/11/18
归纳与抽象
50

可变长字典参数传递方法
2024/11/18
归纳与抽象
50', 'fe5122e4851c2dab406af18183cee28e2cc28b76f92c317d435075b5fcd02d53', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '可变长字典参数传递方法
2024/11/18
归纳与抽象
50') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11177, 43, 192, 185, 51, 0, '结合使用 *args 和 **kwargs', '结合使用 *args 和 **kwargs

结合使用 *args 和 **kwargs
可以在同一个函数中同时使用 *args 和 **kwargs 来处理位置参数和关键字参数的组合
2024/11/18
归纳与抽象
51

结合使用 *args 和 **kwargs
可以在同一个函数中同时使用 *args 和 **kwargs 来处理位置参数和关键字参数的组合
2024/11/18
归纳与抽象
51', 'f432cecbb464f1263fb20de04db98f00ac5f64cc200153fc3f9495a75be5c7df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '结合使用 *args 和 **kwargs
可以在同一个函数中同时使用 *args 和 **kwargs 来处理位置参数和关键字参数的组合
2024/11/18
归纳与抽象
51') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11178, 43, 192, 185, 52, 0, '函数递归', '函数递归

函数递归
递归（Recursion）是计算机科学中的一种重要概念，指的是在函数的定义中直接或间接调用函数自身。
递归常用于解决一些问题，这些问题通常可以被分解为规模较小的相似子问题。
通过递归，可以用简洁的代码描述复杂的过程。
2024/11/18
归纳与抽象
52

函数递归
递归（Recursion）是计算机科学中的一种重要概念，指的是在函数的定义中直接或间接调用函数自身。
递归常用于解决一些问题，这些问题通常可以被分解为规模较小的相似子问题。
通过递归，可以用简洁的代码描述复杂的过程。
2024/11/18
归纳与抽象
52', 'b3c43078b45914cab7800a3b872f4fd248b6965ed175280653c3362bd86ce3e7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '函数递归
递归（Recursion）是计算机科学中的一种重要概念，指的是在函数的定义中直接或间接调用函数自身。
递归常用于解决一些问题，这些问题通常可以被分解为规模较小的相似子问题。
通过递归，可以用简洁的代码描述复杂的过程。
2024/11/18
归纳与抽象
52') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11179, 43, 192, 185, 53, 0, '递归的基本概念', '递归的基本概念

递归的基本概念
递归函数是一种直接或间接调用自身的函数。为了让递归有效工作，每个递归过程都应该具备两个基本要素
基准情形（Base Case）：用于结束递归调用的条件。当满足这个条件时，函数将不再调用自身，返回一个明确的结果。
递归情形（Recursive Case）：在每次递归调用中，问题的规模应逐步减小，直到满足基准情形。
2024/11/18
归纳与抽象
53

递归的基本概念
递归函数是一种直接或间接调用自身的函数。为了让递归有效工作，每个递归过程都应该具备两个基本要素
基准情形（Base Case）：用于结束递归调用的条件。当满足这个条件时，函数将不再调用自身，返回一个明确的结果。
递归情形（Recursive Case）：在每次递归调用中，问题的规模应逐步减小，直到满足基准情形。
2024/11/18
归纳与抽象
53', '916a46908a184462f3c7a26044fbc02ce6ddd6057fb34ec57d43dcf5aec23f01', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '递归的基本概念
递归函数是一种直接或间接调用自身的函数。为了让递归有效工作，每个递归过程都应该具备两个基本要素
基准情形（Base Case）：用于结束递归调用的条件。当满足这个条件时，函数将不再调用自身，返回一个明确的结果。
递归情形（Recursive Case）：在每次递归调用中，问题的规模应逐步减小，直到满足基准情形。
2024/11/18
归纳与抽象
53') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11180, 43, 192, 185, 54, 0, '举例：计算阶乘', '举例：计算阶乘

举例：计算阶乘
阶乘（Factorial）是递归的经典例子。一个数的阶乘是该数与所有小于它的正整数的乘积。通常用符号 n! 表示。
定义：
n! = n * (n-1) * (n-2) * ... * 1
例如：5! = 5 * 4 * 3 * 2 * 1 = 120

递归表达：
基准情形：0! = 1（任何数的阶乘都可以递归到 0!）
递归情形：n! = n * (n-1)!
2024/11/18
归纳与抽象
54

举例：计算阶乘
阶乘（Factorial）是递归的经典例子。一个数的阶乘是该数与所有小于它的正整数的乘积。通常用符号 n! 表示。
定义：
n! = n * (n-1) * (n-2) * ... * 1
例如：5! = 5 * 4 * 3 * 2 * 1 = 120

递归表达：
基准情形：0! = 1（任何数的阶乘都可以递归到 0!）
递归情形：n! = n * (n-1)!
2024/11/…', 'cd851837daccc719715261d890629f3b4ca38d108c78d5d6485bc060beefde4a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：计算阶乘
阶乘（Factorial）是递归的经典例子。一个数的阶乘是该数与所有小于它的正整数的乘积。通常用符号 n! 表示。
定义：
n! = n * (n-1) * (n-2) * ... * 1
例如：5! = 5 * 4 * 3 * 2 * 1 = 120

递归表达：
基准情形：0! = 1（任何数的阶乘都可以递归到 0!）
递归情形：n! = n * (n-1)!
2024/11/…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11181, 43, 192, 185, 55, 0, '举例：计算阶乘', '举例：计算阶乘

举例：计算阶乘
2024/11/18
归纳与抽象
55

举例：计算阶乘
2024/11/18
归纳与抽象
55', '4b4f11b35a3f87742baacda8d9862231fd46675f193a192f673ce9595e44c09b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：计算阶乘
2024/11/18
归纳与抽象
55') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11182, 43, 192, 185, 56, 0, '7.2、类和对象', '7.2、类和对象

7.2、类和对象
2024/11/18
归纳与抽象
56

7.2、类和对象
2024/11/18
归纳与抽象
56', 'dd02f6620646516126a15abecd5a9fe5f9299e7536350e9842265320c4874320', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.2、类和对象
2024/11/18
归纳与抽象
56') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11183, 43, 192, 185, 57, 0, '7.2.1、类和对象的创建', '7.2.1、类和对象的创建

7.2.1、类和对象的创建
2024/11/18
归纳与抽象
57

7.2.1、类和对象的创建
2024/11/18
归纳与抽象
57', '652d127acffc8b0b50f696c105e25979260aa0c4f5df06e5c72b28cf7d753e68', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.2.1、类和对象的创建
2024/11/18
归纳与抽象
57') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11184, 43, 192, 185, 58, 0, '简单的例子', '简单的例子

简单的例子
小明家有一只猫和一只狗。有一天，小明打开门，小狗和小猫用叫声欢迎他。小明给它们分别喂了食物
1. 小明打开门
2. 小狗汪汪叫
3. 小猫喵喵叫
4. 小猫吃猫粮
5. 小狗吃骨头
2024/11/18
归纳与抽象
58

简单的例子
小明家有一只猫和一只狗。有一天，小明打开门，小狗和小猫用叫声欢迎他。小明给它们分别喂了食物
1. 小明打开门
2. 小狗汪汪叫
3. 小猫喵喵叫
4. 小猫吃猫粮
5. 小狗吃骨头
2024/11/18
归纳与抽象
58', '48ecaab01b8a8cae2fd1989feab29d190fa9f686bf158ebd325121e4f0c952cc', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '简单的例子
小明家有一只猫和一只狗。有一天，小明打开门，小狗和小猫用叫声欢迎他。小明给它们分别喂了食物
1. 小明打开门
2. 小狗汪汪叫
3. 小猫喵喵叫
4. 小猫吃猫粮
5. 小狗吃骨头
2024/11/18
归纳与抽象
58') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11185, 43, 192, 185, 59, 0, '面向过程的编程', '面向过程的编程

面向过程的编程
2024/11/18
归纳与抽象
59
def opendoor(name):

    print("{}打开门".format(name))

def bark(name, voice):

    print("{}发出{}的声音".format(name, voice))

def eat(name, food):

    print("{}吃{}".format(name,food))


opendoor("小明")
bark("小狼","汪汪")
bark("小虎","喵喵")
bark("小虎","猫粮")
bark("小狼","骨头")

面向过程的编程
2024/11/18
归纳与抽象
59
def opendoor(name):

    print("{}打开门".format(name))

def bark(name, voice):

    print("{}发出{}的声音".format(name, voice))

def eat(name, food):

    print("{}吃{}".format(nam…', 'dcde95eb96f162f76a7bd3fb14315370151655c1e50a4b93bd0d68b74737ad6a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向过程的编程
2024/11/18
归纳与抽象
59
def opendoor(name):

    print("{}打开门".format(name))

def bark(name, voice):

    print("{}发出{}的声音".format(name, voice))

def eat(name, food):

    print("{}吃{}".format(nam…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11186, 43, 192, 185, 60, 0, '面向对象的编程', '面向对象的编程

面向对象的编程
2024/11/18
归纳与抽象
60
opendoor()
bark()
bark()
eat()
eat()

面向对象的编程
2024/11/18
归纳与抽象
60
opendoor()
bark()
bark()
eat()
eat()', 'dce366f036e5da801085e8973adab517df2adf8e208b5b96160516d34205341c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向对象的编程
2024/11/18
归纳与抽象
60
opendoor()
bark()
bark()
eat()
eat()') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11187, 43, 192, 185, 61, 0, '面向过程编程', '面向过程编程

面向过程编程
将问题分解成步骤，然后用函数逐个按次序实现，运行的时候依次调用即可
面向对象编程
需要使用 “类” 和 “对象” 来实现
面向对象编程其实就是对 “类” 和 “对象” 的使用
2024/11/18
归纳与抽象
61
面向过程编程 VS 面向对象编程

面向过程编程
将问题分解成步骤，然后用函数逐个按次序实现，运行的时候依次调用即可
面向对象编程
需要使用 “类” 和 “对象” 来实现
面向对象编程其实就是对 “类” 和 “对象” 的使用
2024/11/18
归纳与抽象
61
面向过程编程 VS 面向对象编程', 'b3d575ce6c6866fde6da437b48f2678c03e0497b6265220eeed788d0b5c15567', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向过程编程
将问题分解成步骤，然后用函数逐个按次序实现，运行的时候依次调用即可
面向对象编程
需要使用 “类” 和 “对象” 来实现
面向对象编程其实就是对 “类” 和 “对象” 的使用
2024/11/18
归纳与抽象
61
面向过程编程 VS 面向对象编程') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11188, 43, 192, 185, 62, 0, '面向对象的编程', '面向对象的编程

面向对象的编程
类：集体
用来描述具有相同的属性和方法的对象的集合
它定义了该集合中每个对象所共有的属性和方法
对象：个体
类的实例
2024/11/18
归纳与抽象
62
小明
小红
小刚
学生
人
大俊
杰伦
上班族

面向对象的编程
类：集体
用来描述具有相同的属性和方法的对象的集合
它定义了该集合中每个对象所共有的属性和方法
对象：个体
类的实例
2024/11/18
归纳与抽象
62
小明
小红
小刚
学生
人
大俊
杰伦
上班族', 'f1a62eeae3176a795077769a645c18d2e59ef5d63e58e2c108d1c556261061dd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向对象的编程
类：集体
用来描述具有相同的属性和方法的对象的集合
它定义了该集合中每个对象所共有的属性和方法
对象：个体
类的实例
2024/11/18
归纳与抽象
62
小明
小红
小刚
学生
人
大俊
杰伦
上班族') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11189, 43, 192, 185, 63, 0, '面向对象的选课', '面向对象的选课

面向对象的选课
每个专业的学生可以看作是不同的类
相同专业的同学的必修课程相同
不同专业的同学的必修课程不同
2024/11/18
归纳与抽象
63

面向对象的选课
每个专业的学生可以看作是不同的类
相同专业的同学的必修课程相同
不同专业的同学的必修课程不同
2024/11/18
归纳与抽象
63', '8be5c7b0c5c6558db0297f22a64e881e6484f7ff24dc30d6bfab74ffc46aa6cb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向对象的选课
每个专业的学生可以看作是不同的类
相同专业的同学的必修课程相同
不同专业的同学的必修课程不同
2024/11/18
归纳与抽象
63') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11190, 43, 192, 185, 64, 0, '创建类', '创建类

创建类
2024/11/18
归纳与抽象
64
class PERSON:

    pass

创建类
2024/11/18
归纳与抽象
64
class PERSON:

    pass', '16fd031427432b9a40baf11a2c3f3cd4fc8def1ae851becc358cb0350319e493', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建类
2024/11/18
归纳与抽象
64
class PERSON:

    pass') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11191, 43, 192, 185, 65, 0, 'pass语句', 'pass语句

pass语句
pass 是空语句，是为了保持程序结构的完整性。
pass 不做任何事情，一般用做占位语句。
2024/11/18
归纳与抽象
65

pass语句
pass 是空语句，是为了保持程序结构的完整性。
pass 不做任何事情，一般用做占位语句。
2024/11/18
归纳与抽象
65', 'b38f3f9cefd7cb94f0da496d62aeaa8c4dcc236124fbc3af243ed699b811aa27', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pass语句
pass 是空语句，是为了保持程序结构的完整性。
pass 不做任何事情，一般用做占位语句。
2024/11/18
归纳与抽象
65') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11192, 43, 192, 185, 66, 0, '创建类的实例', '创建类的实例

创建类的实例
ClassName(parameterlist)
2024/11/18
归纳与抽象
66
class PERSON:

    pass



p1 = PERSON()

创建类的实例
ClassName(parameterlist)
2024/11/18
归纳与抽象
66
class PERSON:

    pass



p1 = PERSON()', '72ceec89eb9cd978943e951424507502ec922af91d59c158dae051643acab07a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建类的实例
ClassName(parameterlist)
2024/11/18
归纳与抽象
66
class PERSON:

    pass



p1 = PERSON()') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11193, 43, 192, 185, 67, 0, '“魔术”方法__init__()', '“魔术”方法__init__()

“魔术”方法__init__()
在创建类后，类通常会自动创建一个__init__()方法
该方法是一个特殊方法，每当创建一个类的新实例时，Python都会自动执行它
__init__()方法的名称中，开头和结尾处时两个下划线（中间没有空格）
self参数是一个指向实例本身的引用
2024/11/18
归纳与抽象
67
class PERSON:

    def __init__(self): 
        print("hello, 新建一个人物实例")

p1 = PERSON()
上一章中，函数只有在被调用的时候才会被使用
类中的__init__()函数比较特殊，因此叫“魔术方法”
self = p1

“魔术”方法__init__()
在创建类后，类通常会自动创建一个__init__()方法
该方法是一个特殊方法，每当创建一个类的新实例时，Python都会自动执行它
__init__()方法的名称中，开头和结尾处时两个下划线（中间没有空格）
self参数是一个指向实例本身的引用
2024/11/18
归纳与抽象
67
class PERSON:

    def __init__(self): …', '6113d92226be86b05de616d3392465b67a76a37816e6f9b3f79d9185eb9ea50b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '“魔术”方法__init__()
在创建类后，类通常会自动创建一个__init__()方法
该方法是一个特殊方法，每当创建一个类的新实例时，Python都会自动执行它
__init__()方法的名称中，开头和结尾处时两个下划线（中间没有空格）
self参数是一个指向实例本身的引用
2024/11/18
归纳与抽象
67
class PERSON:

    def __init__(self): …') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11194, 43, 192, 185, 68, 0, '“魔术”方法__init__()', '“魔术”方法__init__()

“魔术”方法__init__()
初始化函数参数
2024/11/18
归纳与抽象
68
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))

p1 = PERSON("小明")
self = p1, name = "小明"

“魔术”方法__init__()
初始化函数参数
2024/11/18
归纳与抽象
68
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))

p1 = PERSON("小明")
self = p1, name = "小明"', 'f350d4551e889ca2e50875f165ae810bdfeffc353209e37e76803c164878fdf3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '“魔术”方法__init__()
初始化函数参数
2024/11/18
归纳与抽象
68
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))

p1 = PERSON("小明")
self = p1, name = "小明"') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11195, 43, 192, 185, 69, 0, '创建类的共有属性和方法', '创建类的共有属性和方法

创建类的共有属性和方法
类：集体
用来描述具有相同的属性和方法的对象（实例）的集合
它定义了该集合中每个对象（实例）所共有的属性和方法
2024/11/18
归纳与抽象
69

创建类的共有属性和方法
类：集体
用来描述具有相同的属性和方法的对象（实例）的集合
它定义了该集合中每个对象（实例）所共有的属性和方法
2024/11/18
归纳与抽象
69', 'e95e3febeb685d1ac0924edf77a79d1967c8fbf1790c7a84770697ae3464d718', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建类的共有属性和方法
类：集体
用来描述具有相同的属性和方法的对象（实例）的集合
它定义了该集合中每个对象（实例）所共有的属性和方法
2024/11/18
归纳与抽象
69') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11196, 43, 192, 185, 70, 0, '创建实例方法并调用', '创建实例方法并调用

创建实例方法并调用
2024/11/18
归纳与抽象
70
def <方法名>(self, 参数列表):
    <方法体>
<实例名>.<方法名>

创建实例方法并调用
2024/11/18
归纳与抽象
70
def <方法名>(self, 参数列表):
    <方法体>
<实例名>.<方法名>', '5752e9e2655b6acfd707cb56f264ca78274be3948332a73f1d3ffdf04fa0999c', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建实例方法并调用
2024/11/18
归纳与抽象
70
def <方法名>(self, 参数列表):
    <方法体>
<实例名>.<方法名>') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11197, 43, 192, 185, 71, 0, '创建实例方法并调用', '创建实例方法并调用

创建实例方法并调用
2024/11/18
归纳与抽象
71
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))


    def opendoor(self):
        print("打开门")

p1 = PERSON("小明")
p1.opendoor()

创建实例方法并调用
2024/11/18
归纳与抽象
71
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))


    def opendoor(self):
        print("打开门")

p1 = PERSON("小明")
p1.ope…', '4dda46548f9a450c53d53fc3b8d706e6980f8f5fd44ea62c0cc629d55d9bb139', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建实例方法并调用
2024/11/18
归纳与抽象
71
class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))


    def opendoor(self):
        print("打开门")

p1 = PERSON("小明")
p1.ope…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11198, 43, 192, 185, 72, 0, '创建属性并调用', '创建属性并调用

创建属性并调用
类属性
定义在类中，并且在方法体外的属性
可以在类的所有实例之间共享值
举例：班费

实例属性
定义在类的方法中的属性
只作用于当前实例中
举例：成绩
2024/11/18
归纳与抽象
72

创建属性并调用
类属性
定义在类中，并且在方法体外的属性
可以在类的所有实例之间共享值
举例：班费

实例属性
定义在类的方法中的属性
只作用于当前实例中
举例：成绩
2024/11/18
归纳与抽象
72', '01c5915c1d65ae48af5c4ef4214ac39d1eb0940e2e723de56847568c58c6ef65', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建属性并调用
类属性
定义在类中，并且在方法体外的属性
可以在类的所有实例之间共享值
举例：班费

实例属性
定义在类的方法中的属性
只作用于当前实例中
举例：成绩
2024/11/18
归纳与抽象
72') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11199, 43, 192, 185, 73, 0, '创建属性并调用', '创建属性并调用

创建属性并调用
类属性
定义在类中，并且在函数体外的属性
可以在类的所有实例之间共享值

实例属性
定义在类的方法中的属性
只作用于当前实例中
2024/11/18
归纳与抽象
73
可通过类名称或实例名称访问
只能通过实例名称访问

创建属性并调用
类属性
定义在类中，并且在函数体外的属性
可以在类的所有实例之间共享值

实例属性
定义在类的方法中的属性
只作用于当前实例中
2024/11/18
归纳与抽象
73
可通过类名称或实例名称访问
只能通过实例名称访问', 'e017e1b6096780012a171f076f52ef9121e1be5fc21d6f7e79d37e5fcfb57c83', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建属性并调用
类属性
定义在类中，并且在函数体外的属性
可以在类的所有实例之间共享值

实例属性
定义在类的方法中的属性
只作用于当前实例中
2024/11/18
归纳与抽象
73
可通过类名称或实例名称访问
只能通过实例名称访问') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11200, 43, 192, NULL, 74, 0, '2024/11/18', '2024/11/18

2024/11/18
归纳与抽象
74

2024/11/18
归纳与抽象
74', '99c2945e695e8afc2c88e45e48a98833a37bd46b5f3051cbd3e1439ec918ea95', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '2024/11/18
归纳与抽象
74') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11201, 43, 192, 185, 75, 0, '创建属性并访问', '创建属性并访问

创建属性并访问
2024/11/18
归纳与抽象
75
class PERSON:

    leixing = "人物" #类属性创建
    count = 0       #类属性创建
    
    def __init__(self, name):
        self.name = name #实例属性创建
        PERSON.count += 1
        print("创建第{}个{}实例".format(PERSON.count, self.leixing)) #内部类属性调用
        
    def intro(self):
        print("大家好，我叫{}".format(self.name)) #内部实例属性调用

p1 = PERSON("小明")
p2 = PERSON("小红")
p3 = PERSON("小刚")

print("目前一共创建了{}个人物对象".format(PERSON.count)) #外部类属性调用
print("{},{}和{}在路上遇到了，开始自我介绍".format(p1.name,p2.name,p3.name)) #外部实例属性调用
p1.intro() #外部调用方法
p2.intro() #外部调用方法
p3.intro() #外部调用方法

创建属性并访问
2024/11/18
归纳与抽象
75
class PERSON:

    leixing = "人物" #类属性创建
    count = 0       #类属性创建
    
    def __init__(self, name):
        self.name = name #实例属性创建
        PERSON.count += 1
        pr…', 'f6ebed4ec300376f5e78499bc4b79697d04946a45548691bc7139659a805162d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建属性并访问
2024/11/18
归纳与抽象
75
class PERSON:

    leixing = "人物" #类属性创建
    count = 0       #类属性创建
    
    def __init__(self, name):
        self.name = name #实例属性创建
        PERSON.count += 1
        pr…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11202, 43, 192, NULL, 76, 0, '举例', '举例

举例
2024/11/18
归纳与抽象
76

举例
2024/11/18
归纳与抽象
76', '818671b9cb55a14da62b0faec8d9372ed1d015ab03caa2fb2672ba84dccd234d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例
2024/11/18
归纳与抽象
76') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11203, 43, 192, 185, 77, 0, '7.2.2、面向对象编程的特征', '7.2.2、面向对象编程的特征

7.2.2、面向对象编程的特征
2024/11/18
归纳与抽象
77

7.2.2、面向对象编程的特征
2024/11/18
归纳与抽象
77', '31d35a885c3a393208449bc335acdb2aabf9c897ba50dc503baf5c09fde08f4f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.2.2、面向对象编程的特征
2024/11/18
归纳与抽象
77') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11204, 43, 192, 185, 78, 0, '面向对象编程的特征', '面向对象编程的特征

面向对象编程的特征
面向对象的编程提供了一种思维方式，使得程序员在软件设计时，关注的焦点不再只是程序的逻辑流程，而更重要的是程序中对象以及对象之间的关系
Python程序是面向对象的程序，具有三个特性
封装
继承
多态
2024/11/18
归纳与抽象
78

面向对象编程的特征
面向对象的编程提供了一种思维方式，使得程序员在软件设计时，关注的焦点不再只是程序的逻辑流程，而更重要的是程序中对象以及对象之间的关系
Python程序是面向对象的程序，具有三个特性
封装
继承
多态
2024/11/18
归纳与抽象
78', '9b76c61d0fa43ee90bbd99d8ad605a0ec182f97a2ea80f04ee25bae695d5bdb8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '面向对象编程的特征
面向对象的编程提供了一种思维方式，使得程序员在软件设计时，关注的焦点不再只是程序的逻辑流程，而更重要的是程序中对象以及对象之间的关系
Python程序是面向对象的程序，具有三个特性
封装
继承
多态
2024/11/18
归纳与抽象
78') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11205, 43, 192, 185, 79, 0, '封装', '封装

封装
封装，是指将抽象得到的数据和行为相结合，将基本类结构的细节隐藏起来，通过方法接口实现对实例变量的所有访问
2024/11/18
归纳与抽象
79

封装
封装，是指将抽象得到的数据和行为相结合，将基本类结构的细节隐藏起来，通过方法接口实现对实例变量的所有访问
2024/11/18
归纳与抽象
79', '6910d7b6a0a7e5525fcdab3bdfee11e1abef8faff3a9114e12a666364fc834bb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '封装
封装，是指将抽象得到的数据和行为相结合，将基本类结构的细节隐藏起来，通过方法接口实现对实例变量的所有访问
2024/11/18
归纳与抽象
79') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11206, 43, 192, 185, 80, 0, '继承', '继承

继承
当已经存在一个类，需要另外再创建一个和已有类非常相似的类时，通常不必将同一段代码重复多次，而是用继承
在类上添加关联，使得位于下层的类可以“继承”位于关系上层的类的属性
继承有利于代码的复用和规模化
和其他语言不同的是，Python中的类还具有多继承的特性，即一个类可以有多个父类。
2024/11/18
归纳与抽象
80

继承
当已经存在一个类，需要另外再创建一个和已有类非常相似的类时，通常不必将同一段代码重复多次，而是用继承
在类上添加关联，使得位于下层的类可以“继承”位于关系上层的类的属性
继承有利于代码的复用和规模化
和其他语言不同的是，Python中的类还具有多继承的特性，即一个类可以有多个父类。
2024/11/18
归纳与抽象
80', '2d4ae555336180c6e40b907b9d8a47d2215b55518dfbce17125685dc64008e84', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '继承
当已经存在一个类，需要另外再创建一个和已有类非常相似的类时，通常不必将同一段代码重复多次，而是用继承
在类上添加关联，使得位于下层的类可以“继承”位于关系上层的类的属性
继承有利于代码的复用和规模化
和其他语言不同的是，Python中的类还具有多继承的特性，即一个类可以有多个父类。
2024/11/18
归纳与抽象
80') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11207, 43, 192, NULL, 81, 0, '继承语法', '继承语法

继承语法
2024/11/18
归纳与抽象
81

继承语法
2024/11/18
归纳与抽象
81', '3d159173d8c3d22aead9fe64c523839a9e13c3f8b015e2b734b2e9cfc773d0e9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '继承语法
2024/11/18
归纳与抽象
81') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11208, 43, 192, 185, 82, 0, '继承关系', '继承关系

继承关系
2024/11/18
归纳与抽象
82
小明
小红
小刚
学生
人
大俊
杰伦
上班族
上课
自习
工作
开会
吃饭
睡觉
运动
化妆
打游戏
看视频

继承关系
2024/11/18
归纳与抽象
82
小明
小红
小刚
学生
人
大俊
杰伦
上班族
上课
自习
工作
开会
吃饭
睡觉
运动
化妆
打游戏
看视频', '6f9960b78a4d379bde2565dc1d1271bad60faeacf73d2b2a67d542b63e6d6ede', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '继承关系
2024/11/18
归纳与抽象
82
小明
小红
小刚
学生
人
大俊
杰伦
上班族
上课
自习
工作
开会
吃饭
睡觉
运动
化妆
打游戏
看视频') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11209, 43, 192, 185, 83, 0, '2024/11/18', '2024/11/18

2024/11/18
归纳与抽象
83
继承单个父类

2024/11/18
归纳与抽象
83
继承单个父类', '8a3c30f99b29fd1880d796870b5a587c6c590c85a4f726138316cdae7eccf3a3', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '2024/11/18
归纳与抽象
83
继承单个父类') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11210, 43, 192, 185, 84, 0, '2024/11/18', '2024/11/18

2024/11/18
归纳与抽象
84
继承单个父类（2）

2024/11/18
归纳与抽象
84
继承单个父类（2）', 'df14699100f05646f6ccee3d3277e73a806e97d89efa1a0d3280e777a792f3d5', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '2024/11/18
归纳与抽象
84
继承单个父类（2）') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11211, 43, 192, 185, 85, 0, '多态', '多态

多态
是指在事先不知道对象类型（数字、字符串、列表、元组、集合、字典）的情况下，可以自动根据对象的不同类型，执行相应的操作
很多内建运算符以及函数、方法都能体现多态的性质
2024/11/18
归纳与抽象
85
len()

多态
是指在事先不知道对象类型（数字、字符串、列表、元组、集合、字典）的情况下，可以自动根据对象的不同类型，执行相应的操作
很多内建运算符以及函数、方法都能体现多态的性质
2024/11/18
归纳与抽象
85
len()', 'a8f6a6096d41cfc2de4153459542622dc58b2ef611e01cd90d6727593f4af5c8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '多态
是指在事先不知道对象类型（数字、字符串、列表、元组、集合、字典）的情况下，可以自动根据对象的不同类型，执行相应的操作
很多内建运算符以及函数、方法都能体现多态的性质
2024/11/18
归纳与抽象
85
len()') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11212, 43, 192, 185, 86, 0, '举例：绘制图形', '举例：绘制图形

举例：绘制图形
2024/11/18
归纳与抽象
86
class Canvas:
    def draw_pic(self, shape):
        print(''---开始绘图---'')
        shape.draw()

class Rectangle:
    def draw(self):
        print("绘制矩形".format())

class Triangle:
    def draw(self):
        print("绘制三角形".format())

class Circle:
    def draw(self):
        print("绘制圆形".format())

#实例化
c = Canvas()
a1 = Rectangle()
a2 = Triangle()
a3 = Circle()

#类调用
c.draw_pic(a1)
c.draw_pic(a2)
c.draw_pic(a3)
把一个实例shape作为一个参数
该实例可以是一个矩形实例a1，三角形实例a2或者是圆形实例a3

举例：绘制图形
2024/11/18
归纳与抽象
86
class Canvas:
    def draw_pic(self, shape):
        print(''---开始绘图---'')
        shape.draw()

class Rectangle:
    def draw(self):
        print("绘制矩形".format())

class Tr…', '5de26a57e40120473dff8785323d6c92cf932207dd4b54e16c243b47b19d7144', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '举例：绘制图形
2024/11/18
归纳与抽象
86
class Canvas:
    def draw_pic(self, shape):
        print(''---开始绘图---'')
        shape.draw()

class Rectangle:
    def draw(self):
        print("绘制矩形".format())

class Tr…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11213, 43, 192, 185, 87, 0, '7.3、模块化编程', '7.3、模块化编程

7.3、模块化编程
2024/11/18
归纳与抽象
87

7.3、模块化编程
2024/11/18
归纳与抽象
87', '558fd82cfb786b36b1abb77123a7cc7f576fd11fc5dc07634c1f44fb690ca495', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '7.3、模块化编程
2024/11/18
归纳与抽象
87') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11214, 43, 192, 185, 88, 0, '模块化编程', '模块化编程

模块化编程
模块化编程是一种通过将程序分解为多个独立模块或文件来组织代码的技术。
每个模块通常实现特定的功能，并且模块之间可以相互调用。
模块化编程的核心思想是“关注点分离”（Separation of Concerns），即将不同的功能分开，使每个模块专注于解决特定的问题。
2024/11/18
归纳与抽象
88

模块化编程
模块化编程是一种通过将程序分解为多个独立模块或文件来组织代码的技术。
每个模块通常实现特定的功能，并且模块之间可以相互调用。
模块化编程的核心思想是“关注点分离”（Separation of Concerns），即将不同的功能分开，使每个模块专注于解决特定的问题。
2024/11/18
归纳与抽象
88', '4c7ca2ba8a90638be04b53ad4929081209a79b99c193c4006873b6cc2f3af4dd', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '模块化编程
模块化编程是一种通过将程序分解为多个独立模块或文件来组织代码的技术。
每个模块通常实现特定的功能，并且模块之间可以相互调用。
模块化编程的核心思想是“关注点分离”（Separation of Concerns），即将不同的功能分开，使每个模块专注于解决特定的问题。
2024/11/18
归纳与抽象
88') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11215, 43, 192, 185, 89, 0, '模块是什么', '模块是什么

模块是什么
模块的英文是module
在python中，一个扩展名为“.py”的文件就称为一个模块
2024/11/18
归纳与抽象
89

模块是什么
模块的英文是module
在python中，一个扩展名为“.py”的文件就称为一个模块
2024/11/18
归纳与抽象
89', 'db9ab6b8342349344e724f2ff3680283c74ffa7b69dc7456a80cd85c2dc64680', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '模块是什么
模块的英文是module
在python中，一个扩展名为“.py”的文件就称为一个模块
2024/11/18
归纳与抽象
89') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11216, 43, 192, 185, 90, 0, '模块的搜索目录', '模块的搜索目录

模块的搜索目录
当使用import语句导入模块时，默认情况下，会按照以下顺序进行查找：
（1）在当前目录（即执行的Python脚本文件所在目录）下查找
（2）到PYTHONPATH（环境变量）下的每个目录中查找
（3）到Python的默认安装目录下查找
以上各个目录的具体位置保存在标准模块sys的sys.path变量中
2024/11/18
归纳与抽象
90

模块的搜索目录
当使用import语句导入模块时，默认情况下，会按照以下顺序进行查找：
（1）在当前目录（即执行的Python脚本文件所在目录）下查找
（2）到PYTHONPATH（环境变量）下的每个目录中查找
（3）到Python的默认安装目录下查找
以上各个目录的具体位置保存在标准模块sys的sys.path变量中
2024/11/18
归纳与抽象
90', '8033547e7c261867e63e5c7362dce71e3d36c56dee02f3c9c69202defd7c21a4', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '模块的搜索目录
当使用import语句导入模块时，默认情况下，会按照以下顺序进行查找：
（1）在当前目录（即执行的Python脚本文件所在目录）下查找
（2）到PYTHONPATH（环境变量）下的每个目录中查找
（3）到Python的默认安装目录下查找
以上各个目录的具体位置保存在标准模块sys的sys.path变量中
2024/11/18
归纳与抽象
90') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11217, 43, 192, 185, 91, 0, '模块的引用', '模块的引用

模块的引用
方式一：
程序可以调用库名（模块名）中的所有函数，使用库中函数的格式如下：




方式二：
2024/11/18
归纳与抽象
91

模块的引用
方式一：
程序可以调用库名（模块名）中的所有函数，使用库中函数的格式如下：




方式二：
2024/11/18
归纳与抽象
91', 'a04efffe73d5b82a5d6deaf54efc117872f63212b1aaef08531936a9ff21e01a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '模块的引用
方式一：
程序可以调用库名（模块名）中的所有函数，使用库中函数的格式如下：




方式二：
2024/11/18
归纳与抽象
91') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11218, 43, 192, 185, 92, 0, '方式一引用random库', '方式一引用random库

方式一引用random库
2024/11/18
归纳与抽象
92

方式一引用random库
2024/11/18
归纳与抽象
92', '1873040897d95b74d89acb849dfd2839d78e63b34b5fc7a5754774bf09dbd5c8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '方式一引用random库
2024/11/18
归纳与抽象
92') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11219, 43, 192, 185, 93, 0, '方式二引用random库', '方式二引用random库

方式二引用random库
2024/11/18
归纳与抽象
93

方式二引用random库
2024/11/18
归纳与抽象
93', 'a95ed26f88f21b46ce4a97338db8b86f10e73d9a98bc05e2bac051c28c70c0df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '方式二引用random库
2024/11/18
归纳与抽象
93') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11220, 43, 192, 185, 94, 0, 'random模块是以random.py文件形式存在', 'random模块是以random.py文件形式存在

random模块是以random.py文件形式存在
2024/11/18
归纳与抽象
94

random模块是以random.py文件形式存在
2024/11/18
归纳与抽象
94', 'aba65e245c975d0092dc3bee3372f06aa0a796994754676ccfab9ae762f755f8', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'random模块是以random.py文件形式存在
2024/11/18
归纳与抽象
94') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11221, 43, 192, 185, 95, 0, '引用自定义模块', '引用自定义模块

引用自定义模块
2024/11/18
归纳与抽象
95

引用自定义模块
2024/11/18
归纳与抽象
95', '96eda71ae259547ce49cc32dc21d42ff1b52bfd28d58c17b909d79b02ef5fe6f', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '引用自定义模块
2024/11/18
归纳与抽象
95') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11222, 43, 192, 185, 96, 0, '引用自定义模块', '引用自定义模块

引用自定义模块
2024/11/18
归纳与抽象
96
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
    print("连隔壁邻居都猜到我现在的感受")
    print("河边的风 在吹着头发飘动")
    print("牵着你的手 一阵莫名感动")
    print("我想带你 回我的外婆家")
    print("一起看着日落 一直到我们都睡着")
    print("...")
    print("=======简单爱==end=======")
    print("")
def sing_daoxiang():
    #一个函数，实现演唱《稻香》片段
    print("=======稻香==begin========")
    print("对这个世界如果你有太多的抱怨")
    print("跌倒了 就不敢继续往前走")
    print("为什么 人要这么的脆弱 堕落")
    print("请你打开电视看看 多少人")
    print("为生命在努力勇敢的走下去")
    print("我们是不是该知足")
    print("...")
    print("=======稻香==end=======")
    print("")
sing_daoxiang()
sing_daoxiang()
singrobot.py
import singrobot

singrobot.sing_jiandanai()
test.py

引用自定义模块
2024/11/18
归纳与抽象
96
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
    pr…', '18b8f8e338e87b938a42199e36d538a8011a4767fb309eb4884f2879e4c28a0e', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '引用自定义模块
2024/11/18
归纳与抽象
96
def sing_jiandanai():
    #一个函数，实现演唱《简单爱》片段
    print("=======简单爱==begin========")
    print("说不上为什麽 我变得很主动")
    print("若爱上一个人 什麽都会值得去做")
    print("我想大声宣布 对你依依不舍")
    pr…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11223, 43, 192, 185, 97, 0, '引用一个模块，相当于执行了这个模块.py的程序', '引用一个模块，相当于执行了这个模块.py的程序

引用一个模块，相当于执行了这个模块.py的程序
有些代码我们只希望以主程序的形式出现的时候才执行，可以使用以下语句：
2024/11/18
归纳与抽象
97
以主程序的形式执行
if __name__ == "__main__":

引用一个模块，相当于执行了这个模块.py的程序
有些代码我们只希望以主程序的形式出现的时候才执行，可以使用以下语句：
2024/11/18
归纳与抽象
97
以主程序的形式执行
if __name__ == "__main__":', '694b269c76ffd67044534a12b04bb3e5d2b43044edc6141dd0851b5f5feea30b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '引用一个模块，相当于执行了这个模块.py的程序
有些代码我们只希望以主程序的形式出现的时候才执行，可以使用以下语句：
2024/11/18
归纳与抽象
97
以主程序的形式执行
if __name__ == "__main__":') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11224, 43, 192, 185, 98, 0, '查看模块名称变量__name__', '查看模块名称变量__name__

查看模块名称变量__name__
2024/11/18
归纳与抽象
98
只有主程序的模块名称__name__的变量值为__main__

查看模块名称变量__name__
2024/11/18
归纳与抽象
98
只有主程序的模块名称__name__的变量值为__main__', '27602b459efc0dfcb7f38ebae460968af1afa52677a98757ca0d98d514c0d45b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '查看模块名称变量__name__
2024/11/18
归纳与抽象
98
只有主程序的模块名称__name__的变量值为__main__') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11225, 43, 192, 185, 99, 0, 'Python中的包', 'Python中的包

Python中的包
使用模块可以避免函数名和变量名重名引发的冲突
那么，如果模块名重复应该怎么办呢？


包(Package)
包是一个分层次的目录结构，它将一组功能相近的模块组织在一个目录下
简单理解就是“文件夹”，不过在该文件夹下必须存在一个名称为“__init__.py”的文件
2024/11/18
归纳与抽象
99

Python中的包
使用模块可以避免函数名和变量名重名引发的冲突
那么，如果模块名重复应该怎么办呢？


包(Package)
包是一个分层次的目录结构，它将一组功能相近的模块组织在一个目录下
简单理解就是“文件夹”，不过在该文件夹下必须存在一个名称为“__init__.py”的文件
2024/11/18
归纳与抽象
99', '38e5a536a77ccdf16c2daad304b4527efd33a18851779e0ae74bc0ea3345c5cb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'Python中的包
使用模块可以避免函数名和变量名重名引发的冲突
那么，如果模块名重复应该怎么办呢？


包(Package)
包是一个分层次的目录结构，它将一组功能相近的模块组织在一个目录下
简单理解就是“文件夹”，不过在该文件夹下必须存在一个名称为“__init__.py”的文件
2024/11/18
归纳与抽象
99') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11226, 43, 192, 185, 100, 0, '包的管理', '包的管理

包的管理
2024/11/18
归纳与抽象
100

包的管理
2024/11/18
归纳与抽象
100', 'ec9a31483356ad63a5221c5289cb455d1d6f328b020dc29c779da53ab702b7d7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '包的管理
2024/11/18
归纳与抽象
100') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11227, 43, 192, 185, 101, 0, '创建包', '创建包

创建包
创建包实际上就是创建一个文件夹，并且在该文件夹中创建一个名称为“__init__.py”的Python文件夹
在“__init__.py”文件中，可以不编写任何代码，也可以编写一些Python代码
在“__init__.py”文件中所编写的代码，在导入包时会自动执行
2024/11/18
归纳与抽象
101

创建包
创建包实际上就是创建一个文件夹，并且在该文件夹中创建一个名称为“__init__.py”的Python文件夹
在“__init__.py”文件中，可以不编写任何代码，也可以编写一些Python代码
在“__init__.py”文件中所编写的代码，在导入包时会自动执行
2024/11/18
归纳与抽象
101', '14273099cbadd5002d42b60563c39ae0dbe51cab5ad2bf74f09fc422168494b0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '创建包
创建包实际上就是创建一个文件夹，并且在该文件夹中创建一个名称为“__init__.py”的Python文件夹
在“__init__.py”文件中，可以不编写任何代码，也可以编写一些Python代码
在“__init__.py”文件中所编写的代码，在导入包时会自动执行
2024/11/18
归纳与抽象
101') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11228, 43, 192, 185, 102, 0, '使用包', '使用包

使用包
方式1: import   <完整包名>.模块名


方式2: from <完整包名> import <模块名>


方式3: from <完整包名>. <模块名> import <变量/函数名>
2024/11/18
归纳与抽象
102
import myrobot.singrobot

myrobot.singrobot.sing_jiandanai()
from myrobot import singrobot

singrobot.sing_jiandanai()
from myrobot.singrobot import sing_jiandanai

sing_jiandanai()

使用包
方式1: import   <完整包名>.模块名


方式2: from <完整包名> import <模块名>


方式3: from <完整包名>. <模块名> import <变量/函数名>
2024/11/18
归纳与抽象
102
import myrobot.singrobot

myrobot.singrobot.sing_jiandanai()
from myrobot im…', 'cb9bee1d2d26410dee6eb00162760e02e285d92b51fa4bc865d21980c54575c0', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '使用包
方式1: import   <完整包名>.模块名


方式2: from <完整包名> import <模块名>


方式3: from <完整包名>. <模块名> import <变量/函数名>
2024/11/18
归纳与抽象
102
import myrobot.singrobot

myrobot.singrobot.sing_jiandanai()
from myrobot im…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11229, 43, 192, 185, 103, 0, '库', '库

库
Python中库的概念主要是从功能和生态角度定义的，可以理解为Python的一个功能项目
具有某些功能的模块、子包和包的集合都可以称作库
标准库
第三方库
自定义模块库
模块编程：在计算生态思想指导下，编写程序的起点不再是探究每个具体算法的逻辑功能和设计，而是尽可能利用第三方库进行代码复用，探究运用库的系统方法。
2024/11/18
归纳与抽象
103

库
Python中库的概念主要是从功能和生态角度定义的，可以理解为Python的一个功能项目
具有某些功能的模块、子包和包的集合都可以称作库
标准库
第三方库
自定义模块库
模块编程：在计算生态思想指导下，编写程序的起点不再是探究每个具体算法的逻辑功能和设计，而是尽可能利用第三方库进行代码复用，探究运用库的系统方法。
2024/11/18
归纳与抽象
103', '947cd55fafd4683a007a6cd65637bc7203b862c77e2c33a553d536d33ad39cbe', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '库
Python中库的概念主要是从功能和生态角度定义的，可以理解为Python的一个功能项目
具有某些功能的模块、子包和包的集合都可以称作库
标准库
第三方库
自定义模块库
模块编程：在计算生态思想指导下，编写程序的起点不再是探究每个具体算法的逻辑功能和设计，而是尽可能利用第三方库进行代码复用，探究运用库的系统方法。
2024/11/18
归纳与抽象
103') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11230, 43, 192, 185, 104, 0, 'Python的生态', 'Python的生态

Python的生态
Python专门提供了共享库的索引官网PyPI（the Python Package Index），即https://pypi.python.org或https://pypi.org
国内镜像
https://pypi.tuna.tsinghua.edu.cn/simple/
http://mirrors.aliyun.com/pypi/simple/
http://pypi.douban.com/simple/
http://pypi.mirrors.ustc.edu.cn/simple/
2024/11/18
归纳与抽象
104

Python的生态
Python专门提供了共享库的索引官网PyPI（the Python Package Index），即https://pypi.python.org或https://pypi.org
国内镜像
https://pypi.tuna.tsinghua.edu.cn/simple/
http://mirrors.aliyun.com/pypi/simple/
http://pypi.…', 'aabb34fb9e483070642b2c866b8e19c551f9b17c528e7ce9131d701d265f273a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'Python的生态
Python专门提供了共享库的索引官网PyPI（the Python Package Index），即https://pypi.python.org或https://pypi.org
国内镜像
https://pypi.tuna.tsinghua.edu.cn/simple/
http://mirrors.aliyun.com/pypi/simple/
http://pypi.…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11231, 43, 192, 185, 105, 0, '第三方库的安装', '第三方库的安装

第三方库的安装
依照安装方式灵活性和难易程度有3个安装方法，建议依次使用：
pip工具安装：pip install <库名>
自定义安装
文件安装
2024/11/18
归纳与抽象
105

第三方库的安装
依照安装方式灵活性和难易程度有3个安装方法，建议依次使用：
pip工具安装：pip install <库名>
自定义安装
文件安装
2024/11/18
归纳与抽象
105', 'f6a77f3e26385416d9458419916f65f27094ba29fb4feb47d101caa843e9226a', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三方库的安装
依照安装方式灵活性和难易程度有3个安装方法，建议依次使用：
pip工具安装：pip install <库名>
自定义安装
文件安装
2024/11/18
归纳与抽象
105') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11232, 43, 192, 185, 106, 0, 'pip工具安装', 'pip工具安装

pip工具安装
pip是Python内置命令，需要通过命令行执行，执行pip –h命令将列出pip常用的子命令，注意，不要在IDLE环境下运行pip程序
2024/11/18
归纳与抽象
106

pip工具安装
pip是Python内置命令，需要通过命令行执行，执行pip –h命令将列出pip常用的子命令，注意，不要在IDLE环境下运行pip程序
2024/11/18
归纳与抽象
106', '0d984842e30319c34c0518faddeed9348debb3e5a899015092473d428526e44b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
pip是Python内置命令，需要通过命令行执行，执行pip –h命令将列出pip常用的子命令，注意，不要在IDLE环境下运行pip程序
2024/11/18
归纳与抽象
106') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11233, 43, 192, 185, 107, 0, 'pip工具安装', 'pip工具安装

pip工具安装
安装一个库的命令：
pip install <库名>
pip工具默认从网络上下载指定库的安装文件并自动安装到系统中
使用-U标签可以更新已安装库的版本
pip install -U <库名>
卸载一个库的命令：
pip uninstall <库名>
2024/11/18
归纳与抽象
107

pip工具安装
安装一个库的命令：
pip install <库名>
pip工具默认从网络上下载指定库的安装文件并自动安装到系统中
使用-U标签可以更新已安装库的版本
pip install -U <库名>
卸载一个库的命令：
pip uninstall <库名>
2024/11/18
归纳与抽象
107', '64eb9edb2cb39ed2e1b6245e877ea114fe4b39229226c33083d0a057ae41ac9d', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
安装一个库的命令：
pip install <库名>
pip工具默认从网络上下载指定库的安装文件并自动安装到系统中
使用-U标签可以更新已安装库的版本
pip install -U <库名>
卸载一个库的命令：
pip uninstall <库名>
2024/11/18
归纳与抽象
107') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11234, 43, 192, 185, 108, 0, 'pip工具安装', 'pip工具安装

pip工具安装
可以通过list子命令列出当前系统中已经安装的第三方库：
pip list
2024/11/18
归纳与抽象
108

pip工具安装
可以通过list子命令列出当前系统中已经安装的第三方库：
pip list
2024/11/18
归纳与抽象
108', 'c333f955244a724b6b6eafcbcfd7972419e072c8c7c4f7537640276c9d2e1cc9', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
可以通过list子命令列出当前系统中已经安装的第三方库：
pip list
2024/11/18
归纳与抽象
108') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11235, 43, 192, 185, 109, 0, 'pip工具安装', 'pip工具安装

pip工具安装
pip的show子命令列出某个已经安装库的详细信息：
pip show <库名>
2024/11/18
归纳与抽象
109

pip工具安装
pip的show子命令列出某个已经安装库的详细信息：
pip show <库名>
2024/11/18
归纳与抽象
109', '9d9d1959165ecff28d9d2bf4a14e4de02c932a3d5423ff67ab565b7a401fc34b', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
pip的show子命令列出某个已经安装库的详细信息：
pip show <库名>
2024/11/18
归纳与抽象
109') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11236, 43, 192, 185, 110, 0, 'pip工具安装', 'pip工具安装

pip工具安装
Pip的download子命令可以下载第三方库的安装包，但并不安装
pip download <库名>
2024/11/18
归纳与抽象
110

pip工具安装
Pip的download子命令可以下载第三方库的安装包，但并不安装
pip download <库名>
2024/11/18
归纳与抽象
110', '135c202bc5412044134320ff5dcd0b0d3f57f02d2b1f61163ac0db7fbd15c2be', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
Pip的download子命令可以下载第三方库的安装包，但并不安装
pip download <库名>
2024/11/18
归纳与抽象
110') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11237, 43, 192, 185, 111, 0, 'pip工具安装', 'pip工具安装

pip工具安装
pip的search子命令可以联网搜索库名或摘要中的关键字
pip search <拟查询关键字>
2024/11/18
归纳与抽象
111

pip工具安装
pip的search子命令可以联网搜索库名或摘要中的关键字
pip search <拟查询关键字>
2024/11/18
归纳与抽象
111', 'c5ab238192e6881f3069116e86d5332e3afabf78b4f0703d2d2f02d0b6df74df', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', 'pip工具安装
pip的search子命令可以联网搜索库名或摘要中的关键字
pip search <拟查询关键字>
2024/11/18
归纳与抽象
111') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11238, 43, 192, 185, 112, 0, '*第三方库pyinstaller', '*第三方库pyinstaller

*第三方库pyinstaller
Python的.py源文件程序是无法脱离开发环境进行单独运行的。
要将Python程序编译成可执行文件，可通过安装
cx-freeze
nuitka
py2exe
pyinstaller
2024/11/18
归纳与抽象
112

*第三方库pyinstaller
Python的.py源文件程序是无法脱离开发环境进行单独运行的。
要将Python程序编译成可执行文件，可通过安装
cx-freeze
nuitka
py2exe
pyinstaller
2024/11/18
归纳与抽象
112', 'd6d0f5264f6c1cba3a78688ccb899d4c66cc02d0e955d9370a7180861d82f839', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '*第三方库pyinstaller
Python的.py源文件程序是无法脱离开发环境进行单独运行的。
要将Python程序编译成可执行文件，可通过安装
cx-freeze
nuitka
py2exe
pyinstaller
2024/11/18
归纳与抽象
112') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11239, 43, 192, 185, 113, 0, '第三方库pyinstaller', '第三方库pyinstaller

第三方库pyinstaller
安装第三方库：pyinstaller
pip install pyinstaller
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple pyinstaller
pip install https://github.com/pyinstaller/pyinstaller/archive/develop.tar.gz







与pip工具类似，请在命令行下运行pyinstaller程序
2024/11/18
归纳与抽象
113

第三方库pyinstaller
安装第三方库：pyinstaller
pip install pyinstaller
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple pyinstaller
pip install https://github.com/pyinstaller/pyinstaller/archive/develop.ta…', 'e7ce08ecb0d57def98f58cefb97279adc702285ba0ca25820330a69f610126c7', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三方库pyinstaller
安装第三方库：pyinstaller
pip install pyinstaller
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple pyinstaller
pip install https://github.com/pyinstaller/pyinstaller/archive/develop.ta…') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11240, 43, 192, 185, 114, 0, '第三方库pyinstaller', '第三方库pyinstaller

第三方库pyinstaller
使用pyinstaller，生成可执行文件exe
2024/11/18
归纳与抽象
114

第三方库pyinstaller
使用pyinstaller，生成可执行文件exe
2024/11/18
归纳与抽象
114', 'f95d5fea54021967669ca39990f7f4a09396ee4011eb851509ba4749f0b23eeb', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三方库pyinstaller
使用pyinstaller，生成可执行文件exe
2024/11/18
归纳与抽象
114') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11241, 43, 192, 185, 115, 0, '第三方库pyinstaller', '第三方库pyinstaller

第三方库pyinstaller
Pyinstaller peppa.py




Pyinstaller –F peppa.py
2024/11/18
归纳与抽象
115
……

第三方库pyinstaller
Pyinstaller peppa.py




Pyinstaller –F peppa.py
2024/11/18
归纳与抽象
115
……', '9faad51a8ac5b6e8ff0f4021727d47a05374f1c2ab2fea374d38d8f2ca889969', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三方库pyinstaller
Pyinstaller peppa.py




Pyinstaller –F peppa.py
2024/11/18
归纳与抽象
115
……') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_page (id, pack_id, document_id, chapter_id, page_no, chunk_index, title, content_markdown, text_hash, preview_asset_path, excerpt) VALUES (11242, 43, 192, 185, 116, 0, '第三方库pyinstaller', '第三方库pyinstaller

第三方库pyinstaller
2024/11/18
归纳与抽象
116
使用pyinstaller库需要注意以下问题：
文件路径不能出现空格，英文句号，斜杠，冒号等字符
源文件必须是UTF-8编码，暂不支持其他编码类型。采用IDLE编写的源文件都保存为UTF-8编码形式，可直接使用

第三方库pyinstaller
2024/11/18
归纳与抽象
116
使用pyinstaller库需要注意以下问题：
文件路径不能出现空格，英文句号，斜杠，冒号等字符
源文件必须是UTF-8编码，暂不支持其他编码类型。采用IDLE编写的源文件都保存为UTF-8编码形式，可直接使用', '3fef31ab471eb5573760f1dd579b85769a202b5c16dd803e7f31495349487eec', '/home/cypress/Alethicode/deploy/data/language_pack/preview/tasks/42/第七章：归纳与抽象.pdf', '第三方库pyinstaller
2024/11/18
归纳与抽象
116
使用pyinstaller库需要注意以下问题：
文件路径不能出现空格，英文句号，斜杠，冒号等字符
源文件必须是UTF-8编码，暂不支持其他编码类型。采用IDLE编写的源文件都保存为UTF-8编码形式，可直接使用') ON CONFLICT (id) DO UPDATE SET content_markdown = EXCLUDED.content_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1809, 43, 'PY43-KC-001', '中国计算机发展成就', '中国计算机发展成就', 'Computing Development Achievements in China', 179, 1, 'basic', ARRAY[]::text[], '了解中国计算机事业的发展历程，包括从银河一号超级计算机到龙芯CPU的自主研发历程，以及神威·太湖之光超级计算机和量子计算原型机（九章、祖冲之二号）等前沿成就，认识中国在计算机领域的创新贡献。', ARRAY[10707, 10708, 10709, 10710, 10711, 10712, 10713]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1847, 43, 'PY43-KC-002', '计算工具的演变', '计算工具的演变', 'Evolution of Computing Tools', 179, 1, 'basic', ARRAY[]::text[], '了解计算工具从古至今的发展历程，包括手动式工具（结绳计数、算盘）、机械式工具（加法器、乘法机、差分机）、机电式工具（穿孔制表机、电子管）到电子计算机（ENIAC）的演变，理解计算工具发展的内在逻辑和历史意义。', ARRAY[10694, 10695, 10696, 10697, 10698, 10699, 10700, 10701, 10702, 10703]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1848, 43, 'PY43-KC-003', '计算思维的方法与特征', '计算思维的方法与特征', 'Methods and Characteristics of Computational Thinking', 179, 1, 'basic', ARRAY[]::text[], '掌握计算思维的问题求解操作流程（发现问题→抽象建模→算法设计→程序实现→结果评价）；理解计算思维的四大特征（概念化、根本性、以人为中心、数学与工程思维融合）。', ARRAY[10724, 10725, 10726]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1849, 43, 'PY43-KC-004', '计算思维的概念与定义', '计算思维的概念与定义', 'Concept and Definition of Computational Thinking', 179, 1, 'basic', ARRAY[]::text[], '掌握计算思维的经典定义（周以真教授2006年提出）；理解计算思维的本质是抽象和自动化；了解计算思维的提出历史背景及其发展脉络。', ARRAY[10721, 10722, 10723]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1850, 43, 'PY43-KC-005', '计算机发展历程与趋势', '计算机发展历程与趋势', 'Computer Development History and Trends', 179, 1, 'basic', ARRAY[]::text[], '掌握计算机发展的五个时代（代）的划分及特点，了解计算机发展的五大趋势（巨型化、微型化、网络化、智能化、多媒体化），理解计算机技术发展的方向和前景。', ARRAY[10704, 10705, 10706]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1851, 43, 'PY43-KC-006', '计算的概念', '计算的概念', 'Concept of Computation', 179, 1, 'basic', ARRAY[]::text[], '理解计算的本质是基于规则的符号串变换或物理状态变换；认识计算是人类基本技能和科学研究工具，已渗透到各个学科领域并改变了科学家们的思考方式。', ARRAY[10715, 10716]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1852, 43, 'PY43-KC-007', '计算科学与计算机科学', '计算科学与计算机科学', 'Computational Science and Computer Science', 179, 1, 'basic', ARRAY[]::text[], '区分计算科学与计算机科学的定义和研究范畴；掌握计算科学的四大特征（器械化、形式化、自动化、泛在化）；理解计算思维作为三大科学思维之一，与理论思维、实验思维的关系。', ARRAY[10717, 10718, 10719, 10720]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1802, 43, 'PY43-KC-008', 'Python开发环境配置与程序运行', 'python开发环境配置与程序运行', 'Python Development Environment Setup and Program Execution', 180, 2, 'basic', ARRAY[]::text[], 'Python下载安装方法；Python IDLE编辑器的使用；交互式运行环境与脚本文件运行方式', ARRAY[10751, 10752, 10753, 10754, 10755, 10756, 10757, 10758]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1805, 43, 'PY43-KC-009', 'Python语言概述', 'python语言概述', 'Python Language Overview', 180, 2, 'basic', ARRAY[]::text[], 'Python语言的特点与优势（语法简洁、功能强大、开源生态丰富）；Python发展历史（Guido van Rossum、Python 2与Python 3的演进）；为何选择Python作为编程入门语言', ARRAY[10738, 10739, 10740, 10741, 10742, 10743, 10744, 10745, 10746, 10747, 10748, 10749, 10750]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1818, 43, 'PY43-KC-010', '变量与运算符', '变量与运算符', 'Variables and Operators', 180, 2, 'basic', ARRAY[]::text[], '变量的概念、命名规则（允许的字符、禁止规则）和保留字；运算符包括算术运算符（+、-、*、/、%、**、//）、赋值运算符（=及扩展赋值）、比较运算符（==、!=、>、>=、<、<=）、逻辑运算符（and、or、not）', ARRAY[10780, 10781, 10782, 10783, 10784]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1820, 43, 'PY43-KC-011', '基本数据类型', '基本数据类型', 'Basic Data Types', 180, 2, 'basic', ARRAY[]::text[], 'Python基本数据类型，包括数字类型（整数int、浮点数float、复数complex、布尔值bool）和字符串类型（单引号、双引号、三引号表示），强调不同数据类型需要严格区分', ARRAY[10774, 10775, 10776, 10777, 10778]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1821, 43, 'PY43-KC-012', '基础函数', '基础函数', 'Basic Functions', 180, 2, 'basic', ARRAY[]::text[], '函数的概念与抽象作用，内置函数print()的多种输出形式、input()输入函数、eval()字符串转表达式函数', ARRAY[10785, 10786, 10787, 10788]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1833, 43, 'PY43-KC-013', '控制台输入与数据类型转换', '控制台输入与数据类型转换', 'Console Input and Data Type Conversion', 180, 2, 'basic', ARRAY[]::text[], '学习使用input()函数从控制台获取用户输入，理解输入始终为字符串类型；学习使用eval()函数将字符串转换为数字或表达式；掌握将eval()与input()联用实现数值输入的方法。', ARRAY[10787, 10788, 10789]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1842, 43, 'PY43-KC-014', '程序的运行方式', '程序的运行方式', 'Running Python Programs', 180, 2, 'basic', ARRAY[]::text[], '使用IDLE编辑和运行Python程序的基本操作方法，包括File->New File创建程序文件，Run->Run Module执行程序', ARRAY[10757, 10758]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1843, 43, 'PY43-KC-015', '程序设计语言发展历史', '程序设计语言发展历史', 'History of Programming Languages', 180, 2, 'basic', ARRAY[]::text[], '程序设计语言的发展历程与分类，包括机器语言、汇编语言、高级语言的特点与区别；编译型语言与解释型语言的原理、区别及各自优势', ARRAY[10730, 10731, 10732, 10733, 10734, 10735, 10736, 10737]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1844, 43, 'PY43-KC-016', '程序运行原理', '程序运行原理', 'Program Execution Principles', 180, 2, 'basic', ARRAY[]::text[], '程序执行的基本原理：输入数据（内部参数、随机数据、控制台、交互界面、文件、网络等输入方式）、处理数据（执行指令序列）、输出数据（控制台、图形、文件、网络等输出方式）', ARRAY[10759, 10760, 10761, 10762, 10763, 10764, 10765, 10766, 10767, 10768, 10769, 10770, 10771, 10772]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1795, 43, 'PY43-KC-017', '0和1的计算思维', '0和1的计算思维', 'Computing Thinking with 0 and 1', 181, 3, 'basic', ARRAY[]::text[], '理解从语义符号化、符号计算化、计算01化、01自动化到分层构造化、构造集成化的思维演进过程，认识门电路、加法器等基础硬件的层次化构造原理。', ARRAY[10818, 10819, 10820, 10821, 10822, 10823, 10824, 10825]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1796, 43, 'PY43-KC-018', 'format()方法的格式控制', 'format方法的格式控制', 'format() Method Formatting Control', 181, 3, 'basic', ARRAY[]::text[], '掌握Python中format()方法的格式化配置，包括槽的顺序、填充字符、对齐方式、宽度设置、千位分隔符、浮点数精度控制，以及整数和浮点数的不同类型输出格式（b/c/d/o/x/X和e/E/f/%）。', ARRAY[10890, 10891, 10892, 10893, 10894]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1803, 43, 'PY43-KC-019', 'Python数值类型与运算', 'python数值类型与运算', 'Python Numeric Types and Operations', 181, 3, 'basic', ARRAY[]::text[], 'Python的四种数字类型（整数、浮点数、复数、布尔值）、内置数值运算操作符和函数、以及类型转换函数int()、float()、complex()的使用。', ARRAY[10831, 10832, 10833, 10834, 10835]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1807, 43, 'PY43-KC-020', 'Unicode编码与字符函数', 'unicode编码与字符函数', 'Unicode Encoding and Character Functions', 181, 3, 'basic', ARRAY[]::text[], '掌握Unicode统一字符编码标准及其代码点概念，学会使用chr()和ord()函数进行Unicode码位与字符之间的转换。', ARRAY[10865, 10876, 10877]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1811, 43, 'PY43-KC-021', '二进制四则运算', '二进制四则运算', 'Binary Arithmetic Operations', 181, 3, 'basic', ARRAY[]::text[], '掌握二进制数的基本加、减、乘、除运算规则，理解逢二进一、借一当二的原则。', ARRAY[10815, 10816]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1826, 43, 'PY43-KC-022', '字符串处理方法', '字符串处理方法', 'String Processing Methods', 181, 3, 'basic', ARRAY[]::text[], '学习使用strip()系列方法去除字符串首尾空格和换行符，使用split()方法根据分隔符分割字符串，以及使用replace()替换字符。这些是文本数据预处理的基础操作。', ARRAY[10830, 10831, 10837, 10843, 10870, 10871, 10872, 10873, 10874, 10875, 10882, 10883, 10884, 10885]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1827, 43, 'PY43-KC-023', '字符串格式化', '字符串格式化', 'String Formatting', 181, 3, 'basic', ARRAY[]::text[], '掌握format()方法的槽机制和格式控制标记，包括对齐方式、填充字符、宽度设置、数字千位分隔符、浮点数精度控制以及不同进制类型（b/c/d/o/x）的输出格式。', ARRAY[10886, 10887, 10888, 10889, 10890, 10891]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1828, 43, 'PY43-KC-024', '字符串索引与切片', '字符串索引与切片', 'String Indexing and Slicing', 181, 3, 'basic', ARRAY[]::text[], '掌握字符串的正向递增序号和反向递减序号，理解切片操作[M:N]的左闭右开区间特性，以及带步长的切片[M:N:K]的高级用法。', ARRAY[10866, 10867, 10868, 10869]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1834, 43, 'PY43-KC-025', '整数位数提取', '整数位数提取', 'Integer Digit Extraction', 181, 3, 'basic', ARRAY[]::text[], '使用整数除法和取模运算提取多位数的各位数字，包括''掐头去尾''法的原理与应用。', ARRAY[10836, 10837, 10838, 10839, 10840, 10841, 10842, 10843, 10844, 10845, 10846]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1838, 43, 'PY43-KC-026', '文字信息编码', '文字信息编码', 'Text Information Encoding', 181, 3, 'basic', ARRAY[]::text[], '西文字符的ASCII编码原理与结构，以及汉字编码体系，包括输入码、国标码、机内码等不同层面的编码方式。', ARRAY[10852, 10853, 10854, 10855, 10856, 10857, 10858, 10859, 10860, 10861]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1841, 43, 'PY43-KC-027', '汉字编码体系', '汉字编码体系', 'Chinese Character Encoding System', 181, 3, 'basic', ARRAY[]::text[], '了解汉字在计算机中的编码方式，包括国标码（GB2312-80）、机内码和字形码。字形码分为点阵式和矢量式两种存储方式，各有优缺点。', ARRAY[10860, 10861, 10862, 10863, 10864]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1845, 43, 'PY43-KC-028', '第三方库的使用', '第三方库的使用', 'Using Third-party Libraries', 181, 3, 'basic', ARRAY[]::text[], 'Python程序中引入和使用第三方库的方法，包括random库的不同引用方式。', ARRAY[10847, 10848, 10849, 10850, 10851]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1854, 43, 'PY43-KC-029', '进位计数制与二进制', '进位计数制与二进制', 'Positional Number Systems and Binary', 181, 3, 'basic', ARRAY[]::text[], '理解进位计数制的概念、基数与位权的含义，认识二进制为何成为计算机内部表示信息的最佳选择（物理实现简单、运算规则简单、适合逻辑运算、抗干扰能力强）。', ARRAY[10804, 10805, 10806, 10807, 10808, 10809, 10810, 10811, 10812, 10813, 10814]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1855, 43, 'PY43-KC-030', '进制转换', '进制转换', 'Number System Conversion', 181, 3, 'basic', ARRAY[]::text[], '掌握十进制与二进制之间的互相转换方法：整数部分除2取余法、小数部分乘2取整法，以及二进制数按位权展开转换为十进制数的方法。', ARRAY[10827, 10828, 10829, 10830]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1797, 43, 'PY43-KC-031', 'for循环与遍历', 'for循环与遍历', 'for Loop and Iteration', 182, 4, 'basic', ARRAY[]::text[], '掌握Python中for语句的遍历循环用法，理解range()函数的参数（start、end、step）及左闭右开区间的特性，能够遍历字符串、列表等可迭代对象。理解确定次数循环与遍历结构的关系。', ARRAY[10945, 10946, 10947, 10948, 10949, 10950, 10951]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1808, 43, 'PY43-KC-032', 'while循环与循环分支结合', 'while循环与循环分支结合', 'while Loop and Loop-Branch Integration', 182, 4, 'basic', ARRAY[]::text[], '掌握while无限循环语句的用法，理解其与确定次数循环的区别。了解死循环的概念及其危害。能够将循环结构与分支语句结合使用，处理如计算π的近似值、凯撒密码等实际问题。', ARRAY[10943, 10944, 10952, 10953, 10954, 10955, 10956, 10957, 10958, 10959]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1815, 43, 'PY43-KC-033', '分支结构', '分支结构', 'Branching Structure', 182, 4, 'basic', ARRAY[]::text[], '根据条件真假选择不同代码路径的控制结构。包括单分支if语句、二分支if-else语句、多分支if-elif-else语句。条件判断使用关系操作符和逻辑运算符（AND、OR、NOT）组合多个条件。', ARRAY[10911, 10912, 10913, 10914, 10915, 10916, 10917, 10918, 10919, 10920, 10921, 10922, 10923, 10924, 10925, 10926, 10927, 10928, 10929]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1816, 43, 'PY43-KC-034', '分支语句嵌套', '分支语句嵌套', 'Nested Conditional Statements', 182, 4, 'basic', ARRAY[]::text[], '理解分支语句的嵌套结构，掌握if-elif-else语句的嵌套方法，用于处理多层次、递进关系的条件判断。典型应用包括用户登录验证、合法结婚年龄判断等多层条件判断场景。', ARRAY[10933, 10934, 10935, 10936, 10937, 10938, 10939, 10940, 10941, 10942]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1822, 43, 'PY43-KC-035', '多条件判断与逻辑运算', '多条件判断与逻辑运算', 'Multi-condition Judgment and Logical Operations', 182, 4, 'basic', ARRAY[]::text[], '掌握Python中if语句的多条件判断，包括逻辑运算符or、and、not的使用，能够使用复合条件表达式进行程序逻辑判断，如判断三角形构成的边长条件。', ARRAY[10928, 10929, 10930, 10931, 10932]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1831, 43, 'PY43-KC-036', '循环嵌套', '循环嵌套', 'Nested Loops', 182, 4, 'basic', ARRAY[]::text[], '掌握循环语句的嵌套使用，理解双层及多层循环的执行原理，能使用嵌套循环解决复杂问题。掌握循环嵌套与分支语句的结合使用。', ARRAY[10958, 10959, 10960, 10961, 10962, 10963]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1832, 43, 'PY43-KC-037', '循环控制语句', '循环控制语句', 'Loop Control Statements', 182, 4, 'basic', ARRAY[]::text[], '理解break语句（中断当前循环）和continue语句（中断本次循环）的作用和区别，掌握半路循环的实现方式，能在循环中正确使用控制语句实现程序流程的灵活控制。', ARRAY[10964, 10965, 10966, 10967, 10968, 10969, 10970, 10971]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1859, 43, 'PY43-KC-038', '顺序结构', '顺序结构', 'Sequential Structure', 182, 4, 'basic', ARRAY[]::text[], '程序自上而下依次执行各条语句的基本执行流程，包括数据输入、运算处理、结果输出三个环节。重点掌握赋值语句的各种形式：基本赋值、序列赋值、多目标赋值和复合赋值运算符。', ARRAY[10901, 10902, 10903, 10904, 10905, 10906, 10907, 10908, 10909, 10910]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1806, 43, 'PY43-KC-039', 'Random模块与列表', 'random模块与列表', 'Random Module with Lists', 183, 5, 'basic', ARRAY[]::text[], '使用random模块的choice、shuffle、sample函数对列表进行随机选择、打乱和抽样操作', ARRAY[11007, 11008, 11009, 11010, 11011]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1817, 43, 'PY43-KC-040', '列表基本操作', '列表基本操作', 'Basic List Operations', 183, 5, 'basic', ARRAY[]::text[], '列表作为可变序列类型的特有操作，包括：元素修改、添加元素(append/insert)、删除元素(del/pop/remove)、永久排序(sort)与临时排序(sorted)、以及反转列表(reverse)。', ARRAY[10994, 10995, 10996, 10997, 10998, 10999, 11000, 11001, 11002, 11003, 11004, 11005, 11006]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1823, 43, 'PY43-KC-041', '字典在实际问题中的应用', '字典在实际问题中的应用', 'Dictionary Applications in Practical Problems', 183, 5, 'basic', ARRAY[]::text[], '使用字典解决实际问题，包括统计类问题（如成绩统计）和数据管理类问题（如手机通讯录），掌握字典用于数据存储和快速查找的方法。', ARRAY[11062, 11063, 11064, 11065, 11066, 11067, 11068, 11069, 11070, 11071, 11072, 11073, 11074, 11075, 11076]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1824, 43, 'PY43-KC-042', '字典基本操作', '字典基本操作', 'Dictionary Basic Operations', 183, 5, 'basic', ARRAY[]::text[], '字典的创建、添加元素、删除元素、查找元素、遍历等基本操作。理解字典的键值对结构及其与列表的区别。', ARRAY[11064, 11065, 11066, 11067, 11069, 11071, 11072, 11073, 11074, 11075, 11076]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1825, 43, 'PY43-KC-043', '字典类型', '字典类型', 'Dictionary Type', 183, 5, 'basic', ARRAY[]::text[], '理解字典作为键值对数据结构的概念，掌握字典的创建、添加、访问、修改、删除操作。学会使用索引和get()方法访问值，熟练使用del、pop、popitem、clear删除键值对。掌握字典的遍历方法（遍历键、值、键值对）以及keys()、values()、items()等方法。能够运用字典实现键到值的单向映射，如存储学生信息或实现运算符映射。', ARRAY[11037, 11038, 11039, 11040, 11041, 11042, 11043, 11044, 11045, 11046, 11047, 11048, 11049, 11050, 11051, 11052, 11053, 11054, 11055, 11056, 11057, 11058, 11059, 11060, 11061, 11062, 11063]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1829, 43, 'PY43-KC-044', '序列基本操作', '序列基本操作', 'Basic Sequence Operations', 183, 5, 'basic', ARRAY[]::text[], '序列的通用操作包括：索引访问、下标切片、序列相加、序列乘法、成员资格检查、长度计算、最大值、最小值、计数统计，以及for循环遍历。这些操作适用于字符串、元组和列表三种序列类型。', ARRAY[10982, 10983, 10984, 10985, 10986, 10987, 10988, 10989, 10990, 10991, 10992, 10993]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1830, 43, 'PY43-KC-045', '序列类型', '序列类型', 'Sequence Types', 183, 5, 'basic', ARRAY[]::text[], '序列是有序的元素排列，包括三种类型：字符串(str)、元组(tuple)和列表(list)。其中字符串和元组是不可变类型，列表是可变类型。学生需要理解这三种序列类型的定义、创建方式以及它们之间的区别。', ARRAY[10978, 10979, 10980, 10981]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1856, 43, 'PY43-KC-046', '集合的删除操作', '集合的删除操作', 'Set Deletion Operations', 183, 5, 'basic', ARRAY[]::text[], '掌握Python集合的删除操作方法，包括pop()随机返回并删除元素、remove()移除元素（不存在则报错）、discard()移除元素（不存在不报错）、clear()清空集合。学习使用集合存储不重复数据，如统计收货人名单。', ARRAY[11032, 11033, 11034, 11035, 11036]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1857, 43, 'PY43-KC-047', '集合类型', '集合类型', 'Set Data Type', 183, 5, 'basic', ARRAY[]::text[], '集合类型的定义、特性（无序、元素唯一），以及集合的运算操作符（并集|、差集-、交集&、补集^）、集合判断方法（子集、超集、互斥）、元素增删改查基本操作', ARRAY[11013, 11014, 11015, 11016, 11017, 11018, 11019, 11020, 11021, 11022, 11023, 11024, 11025, 11026, 11027, 11028, 11029, 11030, 11031, 11032, 11033]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1804, 43, 'PY43-KC-048', 'Python文件操作', 'python文件操作', 'Python File Operations', 184, 6, 'basic', ARRAY[]::text[], '掌握Python文件操作的基本流程：使用open()函数打开文件（包含文件地址、打开模式、编码模式三个参数），理解绝对路径与相对路径的区别，选择合适的打开模式，关闭文件', ARRAY[11091, 11092, 11093, 11094, 11095, 11096, 11097, 11098, 11099, 11100, 11101]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1819, 43, 'PY43-KC-049', '基于文件的数据分析', '基于文件的数据分析', 'File-based Data Analysis', 184, 6, 'basic', ARRAY[]::text[], '学习完整的文件数据分析流程：从文件读取结构化数据，根据条件进行数据处理和等级判定，将结果写入新文件。掌握列表存储与逐行读取两种文件处理方法的对比与优化。', ARRAY[11121, 11122, 11123, 11124, 11125, 11126]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1835, 43, 'PY43-KC-050', '文件写入操作', '文件写入操作', 'File Write Operations', 184, 6, 'basic', ARRAY[]::text[], '学习使用f.write()函数将字符串写入文件，掌握w模式（覆盖写入）和a模式（追加写入）的区别，理解write()函数与print()函数的行为差异。', ARRAY[11109, 11110, 11111]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1836, 43, 'PY43-KC-051', '文件系统基础', '文件系统基础', 'File System Fundamentals', 184, 6, 'basic', ARRAY[]::text[], '了解文件系统的基本概念，包括文件和目录的定义、文本文件与二进制文件的区别、字符编码的基本原理（如UTF-8和ANSI编码）', ARRAY[11082, 11083, 11084, 11085, 11086, 11087]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1837, 43, 'PY43-KC-052', '文件读取与数据处理', '文件读取与数据处理', 'File Reading and Data Processing', 184, 6, 'basic', ARRAY[]::text[], '掌握Python读取文件内容的方法（read()和readlines()），使用for循环遍历文件行，处理换行符和空格（strip方法），以及根据分隔符分割字符串（split方法）进行数据解析', ARRAY[11102, 11103, 11104, 11105, 11106, 11107, 11108]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1853, 43, 'PY43-KC-053', '词频统计', '词频统计', 'Word Frequency Counting', 184, 6, 'basic', ARRAY[]::text[], '综合应用字典进行词频统计：使用字典作为计数器存储单词出现次数，掌握字典的get()方法优化计数逻辑，将字典转换为列表进行排序，使用lambda函数配合sort()方法实现按值降序排列。', ARRAY[11112, 11113, 11114, 11115, 11116, 11117, 11118, 11119, 11120]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1798, 43, 'PY43-KC-054', 'lambda函数', 'lambda函数', 'Lambda Functions', 185, 7, 'basic', ARRAY[]::text[], '掌握Python中lambda关键字定义匿名函数的方法，理解lambda函数的语法格式及其与普通def函数的等价关系，能够使用lambda创建简洁的单行函数。', ARRAY[11166, 11167, 11168]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1799, 43, 'PY43-KC-055', 'pyinstaller打包', 'pyinstaller打包', 'pyinstaller Packaging', 185, 7, 'basic', ARRAY[]::text[], '使用pyinstaller将Python源代码打包为可执行文件（exe）的方法，包括安装命令、常用参数（-F参数），以及使用时的注意事项（如文件编码、路径规范）。', ARRAY[11238, 11239, 11240, 11241, 11242]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1800, 43, 'PY43-KC-056', 'Python包', 'python包', 'Python Packages', 185, 7, 'basic', ARRAY[]::text[], '包的概念、创建包的方法（包含__init__.py文件）、以及使用包的三种导入方式：完整包名导入、模块导入、函数/变量导入。', ARRAY[11225, 11226, 11227, 11228]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1801, 43, 'PY43-KC-057', 'Python库与pip工具', 'python库与pip工具', 'Python Libraries and pip Tool', 185, 7, 'basic', ARRAY[]::text[], 'Python库的分类（标准库、第三方库、自定义库）、Python生态与PyPI、pip工具的常用子命令（install、uninstall、list、show、download、search）及其使用方法。', ARRAY[11229, 11230, 11231, 11232, 11233, 11234, 11235, 11236, 11237]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1810, 43, 'PY43-KC-058', '主程序与__name__变量', '主程序与_name_变量', 'Main Program and __name__ Variable', 185, 7, 'basic', ARRAY[]::text[], '理解__name__变量的作用，掌握if __name__ == ''__main__''语句的使用场景，能够判断代码是以主程序形式还是以模块形式被调用执行。', ARRAY[11223, 11224]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1812, 43, 'PY43-KC-059', '函数参数高级用法', '函数参数高级用法', 'Advanced Function Parameters', 185, 7, 'basic', ARRAY[]::text[], '掌握Python函数定义中的默认值参数、带可变数量位置参数的*args语法、带可变数量关键字参数的**kwargs语法，以及两者的组合使用。', ARRAY[11170, 11171, 11172, 11173, 11174, 11175, 11176, 11177]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1813, 43, 'PY43-KC-060', '函数变量的作用域', '函数变量的作用域', 'Variable Scope in Functions', 185, 7, 'basic', ARRAY[]::text[], '理解Python中局部变量和全局变量的区别，包括global关键字的使用，以及局部变量与全局变量同名时的遮蔽规则。', ARRAY[11132, 11133, 11139, 11140, 11141, 11142, 11143, 11144, 11145, 11146, 11147, 11148, 11149, 11150, 11151, 11152, 11153, 11154, 11155, 11156, 11157, 11158, 11159, 11160]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1814, 43, 'PY43-KC-061', '函数递归', '函数递归', 'Function Recursion', 185, 7, 'basic', ARRAY[]::text[], '理解递归的概念，掌握递归函数的基本要素：基准情形（结束条件）和递归情形（规模缩小），能够用递归思想解决实际问题如阶乘计算。', ARRAY[11178, 11179, 11180, 11181]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1839, 43, 'PY43-KC-062', '模块与导入', '模块与导入', 'Modules and Importing', 185, 7, 'basic', ARRAY[]::text[], 'Python模块的概念、模块的两种引用方式（import 模块名 和 from 模块名 import 函数名）、引用自定义模块的方法，以及random等标准库模块的使用。', ARRAY[11217, 11218, 11219, 11220, 11221, 11222]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1840, 43, 'PY43-KC-063', '模块化编程', '模块化编程', 'Modular Programming', 185, 7, 'basic', ARRAY[]::text[], '模块化编程的思想和实践，包括模块的概念（.py文件）、模块搜索目录顺序、以及模块的引用方式（import语句）', ARRAY[11213, 11214, 11215, 11216, 11217, 11218]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1846, 43, 'PY43-KC-064', '类和对象基础', '类和对象基础', 'Classes and Objects Basics', 185, 7, 'basic', ARRAY[]::text[], '理解面向对象编程与面向过程编程的区别，掌握类（作为对象集合的定义）和对象（类的实例）的概念，理解类定义属性和方法、对象调用行为的编程模式。', ARRAY[11182, 11183, 11184, 11185, 11186, 11187, 11188, 11189, 11190, 11191, 11192, 11193, 11194, 11195, 11196, 11197, 11198, 11199, 11201]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_kc (id, pack_id, kc_code, kc_name, name_normalized, name_en, chapter_id, chapter_order, difficulty, prerequisite_codes, summary_markdown, page_refs) VALUES (1858, 43, 'PY43-KC-065', '面向对象编程的特征', '面向对象编程的特征', 'Three Pillars of OOP', 185, 7, 'basic', ARRAY[]::text[], '面向对象编程的三大特性：封装（数据与行为结合，隐藏细节）、继承（子类复用父类属性，支持多继承）、多态（不同对象类型自动执行相应操作）', ARRAY[11203, 11204, 11205, 11206, 11208, 11209, 11210, 11211, 11212]::bigint[]) ON CONFLICT (id) DO UPDATE SET summary_markdown = EXCLUDED.summary_markdown;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2251, 43, 187, 'PY43-KC-016', '圆面积计算', '编写程序计算圆面积。

输入：一个浮点数radius，表示圆的半径。
处理：根据公式 area = π × radius × radius 计算圆面积，其中π取3.1415。
输出：输出计算得到的圆面积area。', '输入：圆半径radius
处理：计算圆面积area = π * radius * radius （此处，π取3.1415）
输出：圆面积area', '一个浮点数radius，表示圆的半径。', '一个浮点数，表示圆的面积（π取3.1415）。', 'exercise', true, '', ARRAY[10771]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2252, 43, 188, 'PY43-KC-025', '举例：个十百千获取', '输入一个四位数，输出它的千位数、百位数、十位数和个位数分别是多少？

示例：
输入：1314
输出：千位数是1，百位数是3，十位数是1，个位数是4', '输入一个四位数，输出它的千位数，百位数，十位数和个位数分别是多少？

例如
输入：1314
输出：千位数是1，百位数是3，十位数是1，个位数是4', '一个四位数（1000到9999之间的整数）', '依次输出千位数、百位数、十位数和个位数，格式为：千位数是X，百位数是X，十位数是X，个位数是X（其中X为对应位的数字）', 'exercise', true, '', ARRAY[10836]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2253, 43, 188, 'PY43-KC-022', '举例：将单个大写字母加密', '编写程序实现凯撒密码加密单个大写字母。用户输入一个明文大写字母，程序输出对应的密文（字母向后偏移3位，Z环绕到C）。', 'character = input("请输入明文：")
password = chr(ord("A") + (ord(character) - ord("A") + 3) % 26)
print("密码为：")
print(password)', '用户输入一个明文大写字母（A-Z）', '输出加密后的密码（大写字母）', 'exercise', true, '', ARRAY[10881]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2254, 43, 188, 'PY43-KC-022', '获取星期字符串', '编写程序，实现获取星期字符串的功能。

输入：一个整数（1-7），表示星期几
输出：对应的星期字符串（星期一、星期二、...、星期日）

示例：
输入：3
输出：星期三', '获取星期字符串\n\n输入：1-7的整数，表示星期几\n\n输出：输入整数对应的星期字符串\n\n例如：输入3，输出 星期三', '一个整数，范围为1到7，分别表示星期一至星期日', '对应的星期字符串，例如：星期一、星期二、星期三、星期四、星期五、星期六、星期日', 'exercise', true, '', ARRAY[10872, 10873, 10874]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2255, 43, 189, 'PY43-KC-032', '4.18 举例：计算π的近似值', '编写程序，使用莱布尼茨公式计算π的近似值。

莱布尼茨公式：π = 4 * (1 - 1/3 + 1/5 - 1/7 + 1/9 - ...)

要求：使用while循环计算，直到最后一项的绝对值小于10^-3为止，输出π的近似值。

提示：每一项的绝对值逐渐减小，当项的绝对值小于10^-3时可以停止计算。', '编写程序，用莱布尼茨公式计算π的近似值，直到最后一项的绝对值小于10-3为止', '无输入要求', '输出一个浮点数，表示π的近似值（保留小数位数根据计算精度确定）', 'exercise', true, '', ARRAY[10955]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2256, 43, 189, 'PY43-KC-031', '4.18 九九乘法表', '计算1到10000偶数自然数之和。编写程序，使用for语句遍历整数序列，筛选出偶数并累加求和。', '编写程序，使用for语句计算1～10000的偶数自然数之和。', '无外部输入。程序直接计算1到10000（包含端点）范围内所有偶数自然数的和。', '输出一个整数，即1到10000所有偶数自然数的和。', 'exercise', true, '', ARRAY[10949, 10950, 10951]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2257, 43, 189, 'PY43-KC-036', '4.18 九九乘法表', '编写程序，使用双重循环输出九九乘法表。

要求：输出一个9行9列的九九乘法表，每行显示从1到该行行号的乘法表达式，格式为"a×b=c"，各表达式之间用制表符分隔。', '编写程序，使用双重循环输出九九乘法表', '无输入', '输出九九乘法表，格式为"a×b=c"，共9行。示例输出格式：
1×1=1
1×2=2	2×2=4
1×3=3	2×3=6	3×3=9
...', 'exercise', true, '', ARRAY[10961, 10962]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2258, 43, 189, 'PY43-KC-037', '4.18 编写程序，从键盘输入密码', '编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。', '编写程序，从键盘输入密码，如果密码长度小于6，则要求重新输入。如果长度等于6，则判断密码是否正确，如果正确则中断循环，否则提示错误并要求继续输入。', '从键盘循环输入密码字符串。当密码长度小于6时程序要求重新输入；当密码长度等于6时程序判断密码是否与预设密码匹配，正确则退出循环，错误则提示错误并继续输入。', '根据不同情况输出：密码长度不足6位时输出重新输入提示；密码错误时输出错误提示；密码正确时输出成功提示并结束程序。', 'exercise', true, '', ARRAY[10968]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2259, 43, 189, 'PY43-KC-037', '4.18 编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完', '程序随机生成1到6之间的整数作为色子点数。用户循环输入猜测的数字，最多有三次机会。', '每次猜测后输出提示：偏大、偏小或正确。三次均猜错时输出机会用完的提示。', 'exercise', true, '', ARRAY[10969]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2260, 43, 189, 'PY43-KC-036', '4.19 三角形图案', '编写程序，使用双重循环输出所示三角形图案。

使用双重循环（嵌套循环）控制行数和每行的字符输出，输出一个直角三角形图案。', '编写程序，使用双重循环输出所示三角形图案', '无输入', '输出一个直角三角形图案，由星号(*)或指定字符组成，具体形状见课件所示', 'exercise', true, '', ARRAY[10963]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2261, 43, 189, 'PY43-KC-034', '举例：BMI 指标计算', 'BMI的定义如下：BMI = 体重（kg）÷身高²（m²）。编写程序，输入体重（kg）和身高（m），计算并输出BMI值。例如，身高1.75米、体重75公斤，BMI值为24.49。', '举例：BMI 指标计算
BMI的定义如下：
BMI = 体重（kg）÷身高2（m2）
例如，一个人身高1.75米、体重75公斤，他的BMI值为24.49', '输入包含两行：第一行是一个浮点数表示体重（kg），第二行是一个浮点数表示身高（m）', '输出一个浮点数，表示计算得到的BMI值', 'exercise', true, '', ARRAY[10941]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2262, 43, 189, 'PY43-KC-033', '举例：三角形判断（多条件判断）', '从键盘输入三条边长（均为整数或浮点数），判断这三条边是否能构成三角形。组成三角形的条件是：任意两边之和大于第三边。如果能构成三角形，输出"可以构成三角形"；否则输出"不能构成三角形"。', '编写程序，从键盘输入三条边，判断是否能够构成一个三角形。如果能，则提示可以构成三角形；如果不能，则提示不能构成三角形。
组成三角形的条件：任意两边之和大于第三边', '三个数字，分别表示三条边的长度，数字之间以空格或换行分隔', '一行字符串，如果能构成三角形则输出"可以构成三角形"，否则输出"不能构成三角形"', 'exercise', true, '', ARRAY[10931, 10932]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2263, 43, 189, 'PY43-KC-038', '举例：圆周长和面积计算', '举例：圆周长和面积计算
编写程序，从键盘输入圆的半径，计算并输出圆的周长和面积', '编写程序，从键盘输入圆的半径，计算并输出圆的周长和面积', '从键盘输入一个浮点数，表示圆的半径', '输出两行内容：第一行是圆的周长，第二行是圆的面积，均保留两位小数', 'exercise', true, '', ARRAY[10904]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2264, 43, 189, 'PY43-KC-038', '举例：平均成绩计算', '举例：平均成绩计算
编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位。
练习：试试将float替换成eval', '编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位', '从键盘输入语文、数学、英语三门功课的成绩，三个数字以空格或换行分隔', '输出一个浮点数，表示三门功课的平均成绩，保留小数点后1位', 'exercise', true, '', ARRAY[10903]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2265, 43, 189, 'PY43-KC-038', '举例：年历输出', '编写程序，从键盘输入年份，输出当年的年历。', '编写程序，从键盘输入年份，输出当年的年历。', '输入年份（一个整数）', '输出当年的年历', 'exercise', true, '', ARRAY[10905]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2266, 43, 189, 'PY43-KC-034', '举例：用户登录', '编写程序，实现一个用户登录系统。从键盘输入用户名和密码，先判断用户名是否正确，如果用户名不正确则直接提示"用户名输入有误"；如果用户名正确则进一步判断密码，并给出判断结果的提示。', '编写程序，实现一个用户登录系统', '从键盘输入用户名和密码（两行字符串，第一行用户名，第二行密码）', '根据登录验证结果输出一行提示信息：用户名错误时输出"用户名输入有误"；密码正确时输出"登录成功"；密码错误时输出"密码错误"', 'exercise', true, '', ARRAY[10934, 10935, 10936]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2267, 43, 189, 'PY43-KC-033', '举例：考试成绩判断', '考试成绩判断程序：给定一个分数score，根据以下规则输出对应等级：score >= 90 输出"优秀"，90 > score >= 75 输出"良好"，75 > score >= 60 输出"及格"，score < 60 输出"不及格"。', '举例：考试成绩判断
如果 score 大于或等于 90，程序会输出 "优秀"。
如果 score 小于 90 但大于或等于 75，程序会输出 "良好"。
如果 score 小于 75 但大于或等于 60，程序会输出 "及格"。
如果 score 小于 60，程序会输出 "不及格"。', '一个整数分数score（0-100之间的整数）', '对应的等级字符串："优秀"、"良好"、"及格"或"不及格"', 'exercise', true, '', ARRAY[10912]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2268, 43, 189, 'PY43-KC-031', '编写程序，使用for语句计算1～10000的自然数之和。', '编写程序，使用for语句计算1～10000的自然数之和。', '举例：自然数之和
编写程序，使用for语句计算1～10000的自然数之和。', '无输入要求，计算1到10000的自然数之和。', '输出1到10000所有自然数的和。', 'exercise', true, '', ARRAY[10948, 10949]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2269, 43, 189, 'PY43-KC-034', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。', '输入一行，包含两个数字（可能为整数或小数）和一个运算符，数字与运算符之间用空格分隔。运算符为 +、-、*、/ 之一。', '输出运算结果（保留小数点后两位，具体精度视语言特性而定），如果运算符不是 +、-、*、/ 之一，则输出错误提示信息。', 'assignment', true, '', ARRAY[10940]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2270, 43, 189, 'PY43-KC-034', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。', '编写程序，开发一个小型计算器。从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果输入的运算符不是这4种，则给出错误提示。', '编写程序，开发一个小型计算器，从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果不是这4种运算符，则给出错误提示。举例：计算器（多分支+嵌套）', '输入包含三行：第一行是一个浮点数（被操作数），第二行是一个浮点数（操作数），第三行是一个字符（运算符，取值为 +、-、*、/ 之一，或其他字符）', '输出为一行：若运算符为 +、-、*、/ 之一，输出两数运算的结果；若运算符非法，输出错误提示信息', 'exercise', true, '', ARRAY[10940]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2271, 43, 189, 'PY43-KC-034', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜游戏，给出猜测结果（太大、太小、成功）的提示。', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜，给出猜测结果（太大、太小、成功）的提示。举例：猜数字（random+分支）', '输入包含一行：一个整数，表示玩家猜测的数字', '输出一行：根据猜测结果输出"太大"、"太小"或"成功"', 'exercise', true, '', ARRAY[10942]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2272, 43, 190, 'PY43-KC-047', '5.12 集合类型的复合赋值操作符', '给定两个集合love_place_set1={"天水","淄博"}和love_place_set2={"哈尔滨","天水"}，使用symmetric_difference_update方法修改集合1，输出修改后集合中只被一个人喜欢的地点（两个集合的对称差集）。', 'love_place_set1.symmetric_difference_update(love_place_set2)
print("只有一个女孩喜欢的地点是:{}".format(love_place_set1))', '无外部输入，程序中已定义两个集合：love_place_set1 = {"天水","淄博"}, love_place_set2 = {"哈尔滨","天水"}', '依次输出：第一行格式为"第一个人喜欢的地点是:{}"显示原始集合1，第二行格式为"第二个人喜欢的地点是:{}"显示原始集合1（注意原代码此处有误，应显示集合2），第三行格式为"只有一个女孩喜欢的地点是:{}"显示对称差集结果。', 'worked_example', true, '', ARRAY[11023]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2273, 43, 190, 'PY43-KC-041', '举例：成绩统计', '某班级学生成绩分别为 ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]，使用字典统计成绩的分布情况，输出每个成绩等级的个数。', '第一步，明确成绩统计的数据类型→字典类型
{''A'': 3, ''B'': 4, ''F'': 3}
字典的键 --》 统计项
字典的值 --》 次数', '输入为一个包含成绩等级的列表，如：["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]', '输出为字典格式，键为成绩等级，值为该等级的个数。例如：{''A'': 3, ''B'': 4, ''F'': 3}', 'exercise', true, '', ARRAY[11064, 11065]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2274, 43, 190, 'PY43-KC-041', '举例：成绩统计', '成绩统计程序：给定成绩列表["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]，统计各成绩的数量并输出。输入：成绩列表scores；输出：逐行打印"A有X个"、"B有Y个"、"F有Z个"。使用字典存储统计结果，遍历成绩列表，用if-else或dict.get()方法统计每个成绩出现的次数，最后遍历字典输出结果。', 'scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]

score_dict = {}
for score in scores:
    if score in score_dict:
        score_dict[score] += 1
    else:
        score_dict[score] = 1
for score, count in score_dict.items():
    print("{}有{}个".format(score, count))', '无输入。程序中固定成绩列表：scores = ["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]', '逐行打印每个成绩及其出现次数，格式为"{成绩}有{数量}个"，例如"A有3个"、"B有4个"、"F有3个"', 'worked_example', true, '', ARRAY[11065, 11066, 11067]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2275, 43, 190, 'PY43-KC-041', '举例：手机通讯录管理', '实现一个字典类型的手机通讯录管理系统。功能要求：1. 添加联系人（输入姓名和电话存入字典）；2. 删除联系人（从字典中删除指定联系人）；3. 查找联系人（根据姓名查找电话）；4. 显示电话本（遍历并打印所有联系人）；0. 退出程序。使用while循环配合分支语句构建菜单框架。示例代码实现了完整的交互式通讯录管理功能。', '#手机通讯录管理V1
print("通讯录功能：1.添加联系人 2. 删除联系人 3:查找联系人 4:电话本显示 0:退出：")
contact_dict = {}', '程序运行时通过交互式菜单接收用户选择和联系人信息，包括：(1)菜单选项数字(0-4)；(2)联系人姓名；(3)联系人电话', '根据用户操作输出相应结果：添加成功提示"联系人XXX已成功保存到通讯录!!"；删除成功提示"联系人XXX已删除!!"；查找成功显示"联系人XXX的电话为：YYY"；显示电话本时逐行打印"姓名:电话"；操作失败显示"联系人XXX不存在!!"；退出时结束程序', 'worked_example', true, '', ARRAY[11068, 11069, 11070, 11071, 11072, 11073, 11074, 11075, 11076]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2276, 43, 190, 'PY43-KC-047', '举例：统计需要取快递人员的名单', '统计需要取快递人员的名单。程序循环接收用户输入的收货人姓名，将每个姓名加入集合中，当输入 EXIT 时退出循环，并打印所有已录入的收货人名单。', '举例：统计需要取快递人员的名单
大学城每天都能收到很多快递。请为快递员写一个程序统计一下收到快递的人员名单，以便统一通知。', '多行输入，每行一个收货人姓名，以单独一行 "EXIT" 结束输入。', '先输出"结束录入！"，再输出"所有收货人名单如下："，最后按任意顺序输出所有已录入的收货人姓名（每行一个）。', 'worked_example', true, '', ARRAY[11035, 11036]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2277, 43, 190, 'PY43-KC-042', '举例：运算器', '编写一个计算器程序，实现以下功能：
1. 输入两个数字
2. 输入一个运算符（+、-、*、/）
3. 输出运算结果
4. 若输入其他符号，退出程序', '输入两个数字，并输入加减乘除运算符号，输出运算结果。若输入其他符号，则退出程序', '输入包含多行：第一行是一个运算符（+、-、*、/或其他字符），第二行和第三行分别是两个数字。若运算符为+、-、*、/之一，则继续计算；若为其他字符，则程序结束。', '如果运算符有效，输出两个数字的运算结果（保留两位小数）。如果运算符无效，程序退出（无输出）。', 'exercise', true, '', ARRAY[11062, 11063]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2278, 43, 190, 'PY43-KC-047', '交集', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），输出两个集合的交集，即两个人都喜欢的地点。集合元素为字符串。', 'common_place_set = love_place_set1.intersection(love_place_set2)
print("两个人都喜欢的地点是:{}".format(common_place_set))', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为两个集合的交集（使用intersection或&运算符），即同时存在于两个集合中的元素。', 'exercise', true, '', ARRAY[11021]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2279, 43, 190, 'PY43-KC-047', '差集', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），输出两个集合的差集结果。第一行输出第一个集合中独有的元素，第二行输出第二个集合中独有的元素。集合元素为字符串。', 'onlylove_place_set1 = love_place_set1.difference(love_place_set2)
print("第一个人的唯粉地点是:{}".format(onlylove_place_set1))

onlylove_place_set2 = love_place_set2 - love_place_set1
print("第二个人的唯粉地点是:{}".format(onlylove_place_set2))', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '两行输出，第一行为第一个集合减去第二个集合后的结果（使用difference或-运算符），第二行为第二个集合减去第一个集合后的结果。', 'exercise', true, '', ARRAY[11020]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2280, 43, 190, 'PY43-KC-047', '补集', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），输出两个集合的对称差集，即只被其中一个集合包含而不在另一个集合中的元素。集合元素为字符串。', 'onelove_place_set = love_place_set1.symmetric_difference(love_place_set2)
print("只有一个女孩喜欢的地点是:{}".format(onelove_place_set))', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为两个集合的对称差集（使用symmetric_difference或^运算符），即只出现在一个集合中的元素。', 'exercise', true, '', ARRAY[11022]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2281, 43, 190, 'PY43-KC-041', '遍历字典中的所有键', '编写一个运算器程序。程序接收两个数字和四则运算符（+、-、*、/），输出运算结果。如果输入运算符不是加减乘除中的任何一个，则退出程序。', '举例：运算器
输入两个数字，并输入加减乘除运算符号，输出运算结果。若输入其他符号，则退出程序', '输入一行，包含两个数字和一个运算符，数字和运算符之间用空格分隔。例如：10 5 +', '输出两个数字的运算结果。如果输入的运算符有效，输出格式为：结果为xxx（保留小数点后两位）。如果运算符无效则程序结束。', 'exercise', true, '', ARRAY[11062]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2282, 43, 190, 'PY43-KC-047', '集合类型的复合赋值操作符', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），使用复合赋值操作符symmetric_difference_update将第一个集合更新为两个集合的对称差集，然后输出更新后的第一个集合。集合元素为字符串。', 'love_place_set1.symmetric_difference_update(love_place_set2)
print("只有一个女孩喜欢的地点是:{}".format(love_place_set1))', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为使用symmetric_difference_update更新后的第一个集合（即只出现在一个集合中的元素）。', 'exercise', true, '', ARRAY[11023]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2283, 43, 191, 'PY43-KC-053', '6.36 词频统计问题', '词频统计
给定一篇文章，统计其中共出现了多少词，每个词出现的次数。

基本要求：
1. 读取输入的文章内容
2. 统计文章中出现的词语总数
3. 统计每个词出现的次数
4. 输出词频统计结果', '给定一篇文章，统计其中共出现了多少词，每个词出现的次数', '一篇文章的完整文本内容，可能包含中英文和标点符号', '第一行输出词的总数量，第二行按出现次数从多到少输出每个词及其出现次数，格式为：词:次数', 'exercise', true, '', ARRAY[11112, 11113]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2284, 43, 191, 'PY43-KC-053', '6.41 关键步骤3：对单词的统计值从高到底排序', '实现一个函数，对给定的词频统计字典进行排序。

函数名：sort_word_counts
输入：第一行是一个整数n，表示词频统计字典中的词数。接下来有n行，每行包含一个单词和对应的出现次数，用空格分隔。
输出：按照出现次数从高到低输出排序后的结果。每行输出一个单词及其次数，用空格分隔，格式为：单词 次数。

示例输入：
4
the 1138
tragedy 3
of 669
hamlet 462

示例输出：
the 1138
of 669
hamlet 462
tragedy 3', 'items.sort(key=lambda x:x[1], reverse = True)', '第一行整数n表示词数（1≤n≤1000），接下来n行每行一个单词及其出现次数，单词为不含空格的字符串，次数为正整数。', '按出现次数从高到低输出所有单词及次数，每行一个，格式为「单词 次数」。', 'exercise', true, '', ARRAY[11117]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2285, 43, 191, 'PY43-KC-049', '"car_data.txt"文件是以英文逗号分隔的数据文本文件（文件局部如图所示），记录了某出租汽车公司部分车辆某日0：00~23：00的车辆位置，无标题行。对应列分别是时间、车牌号、北纬、东经。因需要协助查找该日发生在北纬31.2222-31.2333，东经121.45-121.55区域内的案件，编写程序，找到并打印位于该区域内该出租车公司的车辆信息', '给定文件car_data.txt，该文件为CSV格式（无标题行），每行包含4个字段：时间、车牌号、北纬、东经。编写程序读取该文件，筛选出位于指定地理区域（北纬31.2222-31.2333，东经121.45-121.55）内的所有车辆记录，并打印输出符合条件的车辆信息。', '因需要协助查找该日发生在北纬31.2222-31.2333，东经121.45-121.55区域内的案件，编写程序，找到并打印位于该区域内该出租车公司的车辆信息', '文件car_data.txt，每行格式为：时间,车牌号,北纬,东经，各字段以英文逗号分隔，无标题行。例如：13:30,沪A12345,31.2280,121.5000', '打印位于指定区域内所有车辆的信息，每条记录占一行，格式与输入文件相同。筛选条件：31.2222 <= 北纬 <= 31.2333 且 121.45 <= 东经 <= 121.55', 'exercise', true, '', ARRAY[11125, 11126]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2286, 43, 191, 'PY43-KC-049', '举例：统计学科等级水平', '根据考试成绩统计学科等级水平。输入文件score.txt为制表符(\t)分隔的文本文件，无标题行，每行包含考号、程序设计成绩、生物成绩、科学成绩。判定规则：(1) 生物和科学都达到60分且总分达到180分，为及格；(2) 每门课都达到85分且总分达到260分，为优秀；(3) 总分不到180分或有任意一门课不到60分，为不及格。输出文件level.txt每行格式为“考号,等级”。', '编程要求：从用制表符(\t)分割的score.txt文件中读取学生成绩数据，判定等级并写入level.txt文件中。', '文件score.txt，制表符(\t)分隔，无标题行，每行格式：考号 程序设计成绩 生物成绩 科学成绩', '文件level.txt，每行格式：考号,等级（及格/优秀/不及格）', 'exercise', true, '', ARRAY[11122, 11123, 11124]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2287, 43, 191, 'PY43-KC-049', '词频统计代码实现', '读取lemontree.txt文件内容，统计文件中各单词出现频率，输出出现次数最多的前10个单词及出现次数。

输入：当前目录下存在文件lemontree.txt，其中包含英文文本（可能包含逗号、冒号、句点等标点符号）。

输出：输出前10个最高频单词，每行格式为"word===count"（单词===出现次数）。', 'path1 = "lemontree.txt"
f1 = open(path1, ''r'')
content = f1.read()
content = content.lower()
for ch in ['','','':'',''.'']:
    content = content.replace(ch, "")

words = content.split()
counts = {}
for word in words:
    counts[word] = counts.get(word, 0) + 1

items = list(counts.items())
items.sort(key=lambda x:x[1], reverse = True)
for i in range(10):
    word, count = items[i]
    print("{}==={}".format(word, count))', '当前目录下存在文件lemontree.txt，其中包含英文文本（可能包含逗号、冒号、句点等标点符号）。', '输出前10个最高频单词，每行格式为"word===count"（单词===出现次数）。', 'worked_example', true, '', ARRAY[11120]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2288, 43, 192, 'PY43-KC-064', '7.2.1、类和对象的创建', '设计一个简单的面向对象模拟程序。描述：小明家有一只猫和一只狗。小明打开门时，小狗和小猫分别用叫声欢迎他，然后小明给它们分别喂食。要求：创建Dog类和Cat类，每类包含名称属性和打招呼、吃东西的方法。根据描述的事件顺序，依次输出各对象的行为动作。', '小明家有一只猫和一只狗。有一天，小明打开门，小狗和小猫用叫声欢迎他。小明给它们分别喂了食物
1. 小明打开门
2. 小狗汪汪叫
3. 小猫喵喵叫
4. 小猫吃猫粮
5. 小狗吃骨头', '无标准输入要求，按事件顺序自动执行', '按顺序输出：
小狗汪汪叫
小猫喵喵叫
小猫吃猫粮
小狗吃骨头', 'exercise', true, '', ARRAY[11184]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2289, 43, 192, 'PY43-KC-064', '7.2 创建属性并访问', 'class PERSON:
    leixing = "人物"
    count = 0

    def __init__(self, name):
        self.name = name
        PERSON.count += 1
        print("创建第{}个{}实例".format(PERSON.count, self.leixing))

    def intro(self):
        print("大家好，我叫{}".format(self.name))

p1 = PERSON("小明")
p2 = PERSON("小红")
p3 = PERSON("小刚")
print("目前一共创建了{}个人物对象".format(PERSON.count))
print("{},{}和{}在路上遇到了，开始自我介绍".format(p1.name, p2.name, p3.name))
p1.intro()
p2.intro()
p3.intro()', 'class PERSON:\n    leixing = "人物" #类属性创建\n    count = 0       #类属性创建\n    def __init__(self, name):\n        self.name = name #实例属性创建\n        PERSON.count += 1\n        print("创建第{}个{}实例".format(PERSON.count, self.leixing))', '无外部输入，程序中使用硬编码的三个姓名："小明"、"小红"、"小刚"', '创建第1个人物实例\n创建第2个人物实例\n创建第3个人物实例\n目前一共创建了3个人物对象\n小明,小红和小刚在路上遇到了，开始自我介绍\n大家好，我叫小明\n大家好，我叫小红\n大家好，我叫小刚', 'worked_example', false, 'not_stdin_stdout_convertible', ARRAY[11201]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2290, 43, 192, 'PY43-KC-060', '举例：几何形状的面积', '编写函数计算不同几何图形的面积。程序需要分别定义计算矩形、三角形和圆形面积的函数，根据输入的图形类型和相应参数计算并输出面积值。', '举例：几何形状的面积
将每种形状的面积计算逻辑封装在各自的函数中。', '第一行输入一个整数n表示需要计算的图形数量。接下来n行，每行包含一个图形类型和一个或多个参数：对于矩形，输入 "rectangle width height"；对于三角形，输入 "triangle base height"；对于圆形，输入 "circle radius"。所有参数为正数。', '对于每个图形，输出一行该图形的面积，保留两位小数。', 'exercise', false, 'not_stdin_stdout_convertible', ARRAY[11161]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2291, 43, 192, 'PY43-KC-060', '举例：第二种函数定义', 'def sing_happybirthday(name):
    print("Happy birthday to you!")
    print("Happy birthday to you!")
    print("Happy birthday, dear {}!".format(name))
    print("Happy birthday to you!")', 'def sing_happybirthday(name):\n    print("Happy birthday to you!")\n    print("Happy birthday to you!")\n    print("Happy birthday, dear {}!".format(name))\n    print("Happy birthday to you!")', '输入一个字符串，表示朋友的名字。例如：Jay Chou', '输出四行生日歌歌词，第三行应包含输入的名字。输出内容：\nHappy birthday to you!\nHappy birthday to you!\nHappy birthday, dear {名字}!\nHappy birthday to you!', 'exercise', false, 'not_stdin_stdout_convertible', ARRAY[11146, 11147]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2292, 43, 192, 'PY43-KC-061', '举例：计算阶乘', '请编写一个递归函数来计算阶乘。

阶乘的定义：n! = n * (n-1) * (n-2) * ... * 1

递归终止条件（基准情形）：0! = 1
递归关系（递归情形）：n! = n * (n-1)!', '基准情形：0! = 1（任何数的阶乘都可以递归到 0!）递归情形：n! = n * (n-1)!', '一个非负整数 n（0 ≤ n ≤ 20）', '整数 n 的阶乘值 n!', 'exercise', true, '', ARRAY[11180, 11181]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2293, 43, 192, 'PY43-KC-064', '创建实例方法并调用', '创建实例方法并调用

定义一个 PERSON 类，包含：
- __init__ 方法：接收 name 参数，创建实例时输出问候信息
- opendoor 方法：输出"打开门"

创建 PERSON 类的实例 p1（传入"小明"），
然后调用实例的 opendoor 方法。

预期输出：
hello, 新建一个人物实例，名叫【小明】
打开门', 'class PERSON:

    def __init__(self, name):
        print("hello, 新建一个人物实例，名叫【{}】".format(name))


    def opendoor(self):
        print("打开门")

p1 = PERSON("小明")
p1.opendoor()', '无外部输入', '第一行输出: hello, 新建一个人物实例，名叫【小明】
第二行输出: 打开门', 'worked_example', false, 'not_stdin_stdout_convertible', ARRAY[11197]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2294, 43, 192, 'PY43-KC-059', '示例：定义一个接收任意数量关键字参数并打印的函数', '定义一个函数，使用 **kwargs 接收任意数量的关键字参数，将这些参数按 key=value 的格式打印出来。', '示例：定义一个接收任意数量关键字参数并打印的函数', '输入若干形如 key=value 的关键字参数，参数数量不定，参数名和值均为字符串。', '按顺序打印每个关键字参数，格式为 key=value，每个参数占一行。', 'exercise', true, '', ARRAY[11175]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2295, 43, 192, 'PY43-KC-059', '示例：定义一个接收任意数量数字并求和的函数：', '定义一个函数，使用 *args 接收任意数量的位置参数（数字），返回所有参数的和。', '示例：定义一个接收任意数量数字并求和的函数：', '第一行是一个整数N，表示接下来有N个数字需要求和（1≤N≤100）。第二行包含N个整数，数字之间用空格分隔。', '输出这N个数字的和。', 'exercise', true, '', ARRAY[11173]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_example (id, pack_id, document_id, kc_code, title, code, explanation_markdown, input_description, output_description, unit_type, oj_convertible, oj_block_reason, page_refs) VALUES (2296, 43, 192, 'PY43-KC-064', '面向过程的编程', 'def opendoor(name):
    print("{}打开门".format(name))

def bark(name, voice):
    print("{}发出{}的声音".format(name, voice))

def eat(name, food):
    print("{}吃{}".format(name,food))

opendoor("小明")
bark("小狼","汪汪")
bark("小虎","喵喵")
bark("小虎","猫粮")
bark("小狼","骨头")', 'def opendoor(name):\n    print("{}打开门".format(name))\n\ndef bark(name, voice):\n    print("{}发出{}的声音".format(name, voice))', 'No external input required', '小明打开门
小狼发出汪汪的声音
小虎发出喵喵的声音
小虎发出猫粮的声音
小狼发出骨头的声音', 'worked_example', true, '', ARRAY[11185]::bigint[]) ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (476, 43, 'PY43-KC-016', '圆面积计算', '## 题目描述

编写一个程序，根据给定的圆的半径 `radius`，计算该圆的面积。

圆面积的计算公式为：

```
area = π × radius × radius
```

其中 π 取固定值 **3.1415**。

程序需要读取一个浮点数 `radius`，计算并输出对应的面积 `area`。

## 输出要求

输出圆的面积，结果保留小数点后 **4 位**。', '一个浮点数 radius，表示圆的半径。', '输出圆的面积，结果保留小数点后 4 位。', 'Low', '[{"input": "25", "output": "1963.4375"}, {"input": "10", "output": "314.1500"}, {"input": "0.5", "output": "0.7854"}]'::jsonb, '', '[]'::jsonb, '[45]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

radius = float(sys.stdin.readline().strip())
area = 3.1415 * radius * radius
print(f"{area:.4f}")', '本题是让初学者熟悉 Python 的基本输入输出、变量赋值以及算术运算。通过实现一个简单的数学公式，学生可以练习如何把数学表达式转化为代码，并了解控制台 I/O 的基本流程。', '["使用 `math.pi` 而不是题目要求的固定值 3.1415。","忘记将输入的字符串转换为浮点数，导致类型错误。","在计算面积时忘记对 `radius` 进行平方，而是直接使用 `radius`。","输出时使用了多余的格式化或空格，导致答案不匹配。","没有处理输入可能出现的空行或额外空白字符。"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (477, 43, 'PY43-KC-025', '举例：个十百千获取', '输入一个四位数，输出它的千位数、百位数、十位数和个位数分别是多少？

请编写程序，接收一个四位数（1000到9999之间的整数），然后分别提取并输出该数字的千位、百位、十位和个位数字。', '一个四位数（1000到9999之间的整数）', '依次输出千位数、百位数、十位数和个位数，格式为：千位数是X，百位数是X，十位数是X，个位数是X（其中X为对应位的数字）', 'Low', '[{"input": "1314", "output": "千位数是1，百位数是3，十位数是1，个位数是4"}]'::jsonb, '', '[]'::jsonb, '[37]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 读取输入的四位数
n = int(input())

# 提取各位数字：使用整数除法和取模运算
qian = n // 1000       # 千位：除以1000取整
bai = (n // 100) % 10  # 百位：先除以100取整，再对10取模
shi = (n // 10) % 10   # 十位：先除以10取整，再对10取模
ge = n % 10            # 个位：对10取模

# 按指定格式输出
print(f"千位数是{qian}，百位数是{bai}，十位数是{shi}，个位数是{ge}")', '本题是学习整数位提取的基础练习，通过整数除法和取模运算（掐头去尾法）提取多位数的各位数字。这是理解计算机如何处理数字、位运算以及后续学习字符串索引与切片的重要基础。掌握这种方法后，可以推广到提取任意位数数字的场景。', '["百位提取错误：忘记对100取模，导致百位数字不正确","十位提取错误：直接用n // 10而没有再对10取模","输出格式错误：忘记或写错中文冒号、逗号等标点符号","数据类型混淆：将数字与字符串拼接时没有转换类型"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (478, 43, 'PY43-KC-022', '举例：将单个大写字母加密', '编写程序实现凯撒密码加密单个大写字母。

凯撒密码是一种古老的替换加密技术，它将明文中的每个字母按照固定数目进行偏移。

本题要求：
- 用户输入一个明文大写字母（A-Z）
- 程序输出对应的密文
- 字母向后偏移3位
- Z环绕到C（即Z→C，A→D）

**示例**：
- 输入 A，输出 D（A向后移动3位）
- 输入 X，输出 A（X向后移动3位到A）
- 输入 Z，输出 C（Z环绕到C）', '用户输入一个明文大写字母（A-Z）', '输出加密后的密文（大写字母）', 'Low', '[{"input": "A", "output": "D"}, {"input": "Z", "output": "C"}, {"input": "X", "output": "A"}]'::jsonb, '', '[]'::jsonb, '[82]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'char = input()
# 计算加密：原字符偏移3位，Z环绕到C
password = chr(ord(''A'') + (ord(char) - ord(''A'') + 3) % 26)
print(password)', '本题引导初学者理解字符与ASCII码的转换关系（ord和chr函数），并通过模运算实现字母的环绕效果（如Z->A）。这是字符串处理和字符编码的基础练习，也是理解凯撒密码这一经典加密算法的入门案例。', '["忘记对偏移结果取模26，导致输入Z时输出非字母字符","使用硬编码的if-elif语句而不是通用公式","直接输出而没有考虑环绕情况"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (479, 43, 'PY43-KC-024', '获取星期字符串', '编写程序，实现获取星期字符串的功能。

输入一个整数（1-7），分别表示星期一至星期日，程序输出对应的星期字符串。', '一个整数，范围为1到7，分别表示星期一至星期日', '对应的星期字符串，例如：星期一、星期二、星期三、星期四、星期五、星期六、星期日', 'Low', '[{"input": "3", "output": "星期三"}, {"input": "1", "output": "星期一"}, {"input": "7", "output": "星期日"}]'::jsonb, '', '[]'::jsonb, '[73,74,75]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'n = int(input())
weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
print(weekdays[n - 1])', '本题是字符串索引与列表查找的入门练习。学生需要理解如何将用户输入的数字（1-7）映射到对应的中文星期字符串。通过使用列表存储所有星期字符串，然后根据输入的数字访问列表的对应位置，可以培养学生对离散数据映射的理解能力。', '["索引越界：直接使用 n 作为列表索引时忘记 n-1，导致访问 week[n] 时超出范围","硬编码输出：使用多个 if 语句分别处理每个数字，没有利用列表索引的便捷性","列表定义错误：将列表定义为 weekdays = \"星期一,星期二,...\" 字符串形式而非列表"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (480, 43, 'PY43-KC-032', '4.18 举例：计算π的近似值', '编写程序，使用莱布尼茨公式计算π的近似值。

莱布尼茨公式：π = 4 × (1 - 1/3 + 1/5 - 1/7 + 1/9 - ...)

程序从第一项开始累加，交替加减分数（分母为1, 3, 5, 7, 9, ...）。当某一项的绝对值小于给定的阈值时停止计算，输出π的近似值（4乘以累计和）。', '输入一个浮点数，表示计算终止的阈值。当某一项的绝对值小于该阈值时，停止累加。', '输出一个浮点数，表示π的近似值。', 'Low', '[{"input": "0.001", "output": "3.1396"}, {"input": "0.0001", "output": "3.1414"}]'::jsonb, '', '[]'::jsonb, '[58]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 读取阈值
threshold = float(input())

# 初始化变量
denominator = 1
sign = 1
term = 1.0
pi_approx = 0.0

# 使用while循环计算π的近似值
while abs(term) >= threshold:
    # 累加当前项
    pi_approx += term
    # 更新符号（变号）
    sign = -sign
    # 计算下一项的分母
    denominator += 2
    # 计算下一项
    term = sign / denominator

# 输出结果（4乘以累计和）
print(round(pi_approx * 4, 4))', '本题是while循环与循环分支结合的经典练习。通过莱布尼茨公式计算π的近似值，学生需要掌握：1) 使用while循环控制迭代次数；2) 结合分支语句（符号交替）实现数列累加；3) 设置合理的循环终止条件（项的绝对值小于阈值）。这个练习帮助学生理解如何将数学公式转化为程序逻辑，以及如何处理无限级数的近似计算问题。', '["符号变量没有正确更新导致所有项都是正数或负数","循环条件写成 term > threshold 而非 abs(term) >= threshold","在循环内累加前忘记先初始化 pi_approx = 0","分母更新顺序错误，应先加2再计算新项","输出时忘记乘以4，直接输出累计和"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (481, 43, 'PY43-KC-031', '4.18 举例：自然数偶数之和', '编写程序，计算1到指定上限范围内所有偶数自然数的和。

**问题描述**：
给定一个正整数 N（1 ≤ N ≤ 10000），计算 1 到 N 范围内所有偶数自然数（2, 4, 6, ...）的和。如果 N 是奇数，则只计算到 N-1 为止。

**提示**：
- 使用 for 循环配合 range() 函数遍历整数序列
- range() 的第三个参数可以设置步长为 2 来直接生成偶数序列
- 或者在循环内使用条件判断筛选出偶数', '输入一个整数 N（1 ≤ N ≤ 10000），表示求和的上限。', '输出一个整数，即 1 到 N 范围内所有偶数自然数的和。', 'Low', '[{"input": "4", "output": "6"}, {"input": "6", "output": "12"}, {"input": "1", "output": "0"}]'::jsonb, '', '[]'::jsonb, '[52,53,54]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 读取上限 N
N = int(input())

# 初始化累加器
total = 0

# 使用 for 循环遍历 1 到 N 的范围
for i in range(1, N + 1):
    # 判断是否为偶数
    if i % 2 == 0:
        total += i

# 输出结果
print(total)', '本题是Python循环遍历的基础练习，通过计算偶数和让初学者掌握：1）for循环遍历整数序列的用法；2）range()函数的参数设置；3）条件判断筛选数据；4）累加求和的思路。本题也引入了参数化的设计思想，将固定边界改为可输入的变量，增强程序的通用性。', '["错误使用 range(1, N) 而不是 range(1, N+1)，导致漏掉边界值 N","忘记初始化累加器 total = 0","将条件写成 i % 2 != 0（误把奇数当偶数累加）","使用 print(total) 时多输出换行或其他字符"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (482, 43, 'PY43-KC-036', '4.18 九九乘法表', '编写程序，使用双重循环输出乘法表。

程序从标准输入读取一个整数 n，然后输出一个 n×n 的乘法表。

具体要求：
- 乘法表共 n 行
- 第 i 行显示从 1×i 到 i×i 的所有表达式
- 每个表达式格式为 "a×b=c"
- 同一行内各表达式之间用制表符（\t）分隔
- 行末无多余制表符', '输入一个整数 n（1 ≤ n ≤ 9），表示乘法表的行数和列数。', '输出 n 行乘法表，每行格式为 "a×b=c"，表达式之间用制表符分隔。', 'Low', '[{"input": "3", "output": "1×1=1\n1×2=2\t2×2=4\n1×3=3\t2×3=6\t3×3=9"}]'::jsonb, '', '[]'::jsonb, '[64,65]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'n = int(input())
for i in range(1, n + 1):
    row_expressions = []
    for j in range(1, i + 1):
        expr = f"{j}×{i}={j*i}"
        row_expressions.append(expr)
    print("\t".join(row_expressions))', '本问题练习嵌套循环的使用。外层循环控制行数，内层循环控制每行输出的列数。这是经典的循环嵌套应用场景，帮助学生理解双层循环的执行原理和行列关系。每个内层循环遍历到当前行号 i，构造格式为 "a×b=c" 的表达式，最后用制表符连接同一行的表达式。', '["混淆行列关系：内层循环上界应该是 i（当前行号），而不是 n","行末多余制表符：每行最后一个表达式后不应再有制表符，应使用 join 或正确控制输出","换行时机错误：应该在完成一整行的所有表达式后才换行（print 默认换行）","符号错误：使用字母 x 或 * 而非中文乘号 ×"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (483, 43, 'PY43-KC-037', '编写程序，从键盘输入密码', '编写程序，从键盘循环输入密码。如果密码长度小于6位，则输出"密码长度不足6位，请重新输入"并继续输入下一个密码；如果密码长度等于6位，则判断该密码是否与预设密码匹配，如果匹配则输出"密码正确，登录成功"并退出程序，如果不匹配则输出"密码错误，请重新输入"并继续输入下一个密码。', '第一行是一个字符串，表示预设的正确密码。从第二行开始，每行是一个字符串，表示用户依次尝试输入的密码。', '根据每次输入的密码，输出相应的提示信息：
- 当输入密码长度不足6位时，输出"密码长度不足6位，请重新输入"
- 当输入密码长度为6位但与预设密码不匹配时，输出"密码错误，请重新输入"
- 当输入密码长度为6位且与预设密码匹配时，输出"密码正确，登录成功"并结束程序', 'Low', '[{"input": "abc123\nabc123", "output": "密码正确，登录成功"}, {"input": "abc123\nab\nabc123", "output": "密码长度不足6位，请重新输入\n密码正确，登录成功"}, {"input": "abc123\nwrong1\nabc123", "output": "密码错误，请重新输入\n密码正确，登录成功"}]'::jsonb, '', '[]'::jsonb, '[71]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'correct_password = input().strip()
while True:
    password = input().strip()
    if len(password) < 6:
        print("密码长度不足6位，请重新输入")
    elif password != correct_password:
        print("密码错误，请重新输入")
    else:
        print("密码正确，登录成功")
        break', '本题是循环控制语句的经典应用场景。通过while True创建无限循环，使用break语句在密码正确时退出循环，实现''半路循环''的效果。学生需要理解：1) 如何根据条件判断输出不同提示；2) 如何在满足条件时使用break跳出循环；3) 如何处理长度验证和密码匹配两个判断条件。这是从理论到实践的良好过渡。', '["混淆break和continue的用法：break是退出整个循环，而continue是跳过本次循环继续下一次","忘记在密码正确时使用break语句，导致程序无法正常退出","判断条件顺序错误：应该先判断长度是否小于6，再判断长度是否等于6且密码是否正确","输出信息与题目要求不一致，如少了''请重新输入''等字样"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (484, 43, 'PY43-KC-037', '4.18 编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会', '编写程序，随机产生色子的一面（数字1～6），给用户三次猜测机会，程序给出猜测提示（偏大或偏小）。如果某次猜测正确，则提示正确并中断循环；如果三次均猜错，则提示机会用完。

**游戏规则：**
1. 程序先设定一个1到6之间的随机整数作为色子点数
2. 用户有三次猜测机会
3. 每次猜测后，程序输出提示：
   - 若猜测大于色子点数，输出"偏大"
   - 若猜测小于色子点数，输出"偏小"
   - 若猜测等于色子点数，输出"正确"并结束游戏
4. 三次均猜错时，输出"机会用完"', '输入共4个整数，用空格分隔。第一个整数为目标色子点数（1~6），后三个整数为用户依次猜测的数字。', '对每次猜测输出一行提示：
- 偏大（猜测大于目标）
- 偏小（猜测小于目标）
- 正确（猜测正确，游戏结束）

若三次均未猜中，在最后一次提示后输出"机会用完"。', 'Low', '[{"input": "3 1 5 6", "output": "偏小\n偏大\n偏大\n机会用完"}, {"input": "5 1 5 3", "output": "偏小\n正确"}, {"input": "1 6 6 6", "output": "偏大\n偏大\n偏大\n机会用完"}]'::jsonb, '', '[]'::jsonb, '[72]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'data = input().split()
target = int(data[0])

guessed = False
for i in range(1, 4):
    guess = int(data[i])
    if guess > target:
        print("偏大")
    elif guess < target:
        print("偏小")
    else:
        print("正确")
        guessed = True
        break

if not guessed:
    print("机会用完")', '本题是经典的猜数字游戏，用于帮助初学者掌握 while 循环或 for 循环与条件判断的结合使用。核心知识点包括：1) 使用 break 语句提前退出循环，实现''猜中即停''的逻辑；2) 通过循环计数器控制猜测次数；3) 使用 if-elif-else 分支结构输出偏大/偏小/正确的提示；4) 当循环正常结束（未 break）时，说明三次均未猜中，此时输出''机会用完''。这个练习帮助学生理解循环控制语句在程序流程自动化中的重要作用。', '["猜中后没有用 break 语句退出循环，导致继续执行后面的猜测","三次猜错时忘记输出''机会用完''的提示","将''机会用完''的输出放在循环内部而不是循环结束后","条件判断顺序错误，例如先判断偏小再判断偏大导致逻辑混乱"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (485, 43, 'PY43-KC-036', '4.19 三角形图案', '编写程序，使用双重循环（嵌套循环）输出一个直角三角形图案。

给定一个整数 n（1 ≤ n ≤ 20），输出一个 n 行的直角三角形图案，其中第 i 行有 i 个星号(*)。', '输入一个整数 n，表示直角三角形的行数。', '输出一个 n 行的直角三角形图案，第 i 行包含 i 个星号(*)。每行结束时换行。', 'Low', '[{"input": "3", "output": "*\n**\n***"}, {"input": "5", "output": "*\n**\n***\n****\n*****"}]'::jsonb, '', '[]'::jsonb, '[66]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'n = int(input())
for i in range(1, n + 1):
    for j in range(1, i + 1):
        print(''*'', end='''')
    print()', '本题是嵌套循环（双重循环）的经典入门练习。通过外层循环控制行数，内层循环控制每行输出的星号数量，帮助学生理解循环嵌套的执行原理——外层循环执行一次，内层循环完整执行一次。这与打印九九乘法表同属于循环嵌套的典型应用场景，是理解循环嵌套的重要基础练习。', '["内层循环的范围写错，如写成 range(i) 导致少打印一个星号","忘记在外层循环末尾添加 print() 换行","print() 语句的 end 参数设置错误，导致星号连成一行"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (486, 43, 'PY43-KC-034', '举例：BMI 指标计算', 'BMI（身体质量指数）是衡量人体胖瘦程度的一个常用指标，其计算公式如下：

**BMI = 体重（kg）÷ 身高²（m²）**

请编写程序，从标准输入读取体重（单位：千克）和身高（单位：米），计算并输出对应的BMI值。

例如，身高1.75米、体重75公斤时，BMI = 75 ÷ (1.75)² ≈ 24.49', '输入包含两行：
第一行是一个浮点数表示体重（kg）
第二行是一个浮点数表示身高（m）', '输出一个浮点数，表示计算得到的BMI值', 'Low', '[{"input": "75\n1.75", "output": "24.489795918367346"}]'::jsonb, '', '[]'::jsonb, '[44]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'weight = float(input())
height = float(input())
bmi = weight / (height ** 2)
print(bmi)', '本题是流程自动化章节的入门练习，要求学生掌握基本的输入输出操作和算术运算。通过计算BMI这一实际应用，让学生理解如何将数学公式转化为程序代码，同时复习浮点数的使用和基本运算。', '["身高平方计算错误：使用 height * 2 而不是 height * height 或 height ** 2","公式顺序颠倒：写成 height ** 2 / weight 而不是 weight / height ** 2","输入读取错误：没有正确读取两行输入，或忘记转换为浮点数"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (487, 43, 'PY43-KC-035', '三角形判断', '编写程序，从键盘输入三条边长，判断这三条边是否能构成三角形。

**三角形构成条件**：任意两边之和大于第三边。

- 如果能构成三角形，输出：`可以构成三角形`
- 否则输出：`不能构成三角形`', '三个数字，分别表示三条边的长度，数字之间以空格或换行分隔', '一行字符串，如果能构成三角形则输出"可以构成三角形"，否则输出"不能构成三角形"', 'Low', '[{"input": "3 4 5", "output": "可以构成三角形"}, {"input": "1 2 3", "output": "不能构成三角形"}]'::jsonb, '', '[]'::jsonb, '[34,35]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'a, b, c = map(float, input().split())

if a + b > c and a + c > b and b + c > a:
    print("可以构成三角形")
else:
    print("不能构成三角形")', '本题是Python流程控制中的经典多条件判断练习。通过判断三角形边长条件，学生需要同时运用逻辑运算符（and）和多个条件的组合，培养逻辑思维能力。本题也是后续学习更复杂条件判断和循环控制的基础。', '["只检查了 a + b > c 一个条件，忽略了其他两组条件的检查","使用了 or 运算符代替 and，导致条件判断错误","没有考虑边长为0或负数的情况"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (488, 43, 'PY43-KC-038', '举例：圆周长和面积计算', '## 题目描述

编写程序，从键盘输入一个圆的半径，计算并输出该圆的周长和面积。

**提示**：
- 圆的周长公式：$C = 2 \times \pi \times r$
- 圆的面积公式：$S = \pi \times r^2$
- 其中 $\pi$ 可以使用 Python 的 `math.pi` 或近似值 `3.14159`

## 输出要求

- 第一行输出圆的周长
- 第二行输出圆的面积
- 结果均保留两位小数', '从键盘输入一个浮点数，表示圆的半径', '输出两行内容：第一行是圆的周长，第二行是圆的面积，均保留两位小数', 'Low', '[{"input": "1", "output": "6.28\n3.14"}, {"input": "2.5", "output": "15.71\n19.63"}]'::jsonb, '', '[]'::jsonb, '[7]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import math

# 读取圆的半径
r = float(input())

# 计算周长和面积
circumference = 2 * math.pi * r
area = math.pi * r * r

# 输出结果，保留两位小数
print("{:.2f}".format(circumference))
print("{:.2f}".format(area))', '本题是顺序结构的经典练习，展示了程序自上而下依次执行的三环节：数据输入（读取半径）、运算处理（计算周长和面积）、结果输出（打印结果）。通过这道题，初学者可以熟悉变量赋值、基本数学运算和格式化输出，是后续学习循环和分支结构的基础。', '["面积公式错误：误用 r*2*r 而非 r*r，或忘记平方操作","使用错误的π值：直接写死3.14导致精度不足，应使用 math.pi","输出格式错误：忘记保留两位小数，或使用整数除法导致结果截断"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (489, 43, 'PY43-KC-038', '举例：平均成绩计算', '编写程序，从键盘输入语文、数学、英语三门功课的成绩，计算并输出平均成绩，要求平均成绩小数点后保留1位。

练习：试试将float替换成eval', '从键盘输入语文、数学、英语三门功课的成绩，三个数字以空格或换行分隔', '输出一个浮点数，表示三门功课的平均成绩，保留小数点后1位', 'Low', '[{"input": "90 85 95", "output": "90.0"}, {"input": "70 80 90", "output": "80.0"}]'::jsonb, '', '[]'::jsonb, '[6]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

tokens = sys.stdin.read().split()
chinese = float(tokens[0])
math = float(tokens[1])
english = float(tokens[2])
average = (chinese + math + english) / 3
print(f"{average:.1f}")
', '本题是顺序结构的典型应用示例，展示了程序自上而下依次执行数据输入、运算处理、结果输出三个基本环节。通过计算三门功课的平均成绩，帮助初学者理解基本的算术运算、变量赋值和格式化输出。题目要求保留1位小数，涉及到浮点数运算和字符串格式化，是后续学习更复杂数据处理的基础。', '["计算平均值时忘记除以3，导致输出三门成绩之和而非平均值","输出时没有保留1位小数，如直接print(average)导致输出90.0显示为90","使用整数除法//而非浮点数除法/，导致结果为整数","输入使用split()后忘记将字符串转换为浮点数进行计算"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (490, 43, 'PY43-KC-038', '年历输出', '编写程序，从键盘输入年份，输出当年的年历。

年历需要包含一年中所有12个月的信息。每个月需要显示月份名称、星期标题（Sun Mon Tue Wed Thu Fri Sat），以及该月的所有日期。月份之间用一个空行分隔。

**闰年判断规则**：能被4整除但不能被100整除的年份是闰年，或者能被400整除的年份也是闰年。

**输出格式要求**：
- 第一行输出年份（如 2024）
- 接下来按顺序输出1月到12月的月历
- 每个月：第一行是月份名称（如 January 或 1 月）
- 第二行是星期标题：Sun Mon Tue Wed Thu Fri Sat
- 接下来若干行显示该月的日期，右对齐到各自星期列的位置
- 月与月之间用一个空行分隔', '输入一个整数，表示年份。', '输出该年份的完整年历，格式见题目描述。', 'Mid', '[{"input": "2024", "output": "2024\nJanuary\nSun Mon Tue Wed Thu Fri Sat\n       1   2   3   4   5   6\n   7   8   9  10  11  12  13\n  14  15  16  17  18  19  20\n  21  22  23  24  25  26  27\n  28  29  30  31\n\nFebruary\nSun Mon Tue Wed Thu Fri Sat\n                   1   2   3\n   4   5   6   7   8   9  10\n  11  12  13  14  15  16  17\n  18  19  20  21  22  23  24\n  25  26  27  28  29\n\nMarch\nSun Mon Tue Wed Thu Fri Sat\n                       1   2\n   3   4   5   6   7   8   9\n  10  11  12  13  14  15  16\n  17  18  19  20  21  22  23\n  24  25  26  27  28  29  30\n  31\n\nApril\nSun Mon Tue Wed Thu Fri Sat\n       1   2   3   4   5   6\n   7   8   9  10  11  12  13\n  14  15  16  17  18  19  20\n  21  22  23  24  25  26  27\n  28  29  30\n\nMay\nSun Mon Tue Wed Thu Fri Sat\n               1   2   3   4\n   5   6   7   8   9  10  11\n  12  13  14  15  16  17  18\n  19  20  21  22  23  24  25\n  26  27  28  29  30  31\n\nJune\nSun Mon Tue Wed Thu Fri Sat\n                           1\n   2   3   4   5   6   7   8\n   9  10  11  12  13  14  15\n  16  17  18  19  20  21  22\n  23  24  25  26  27  28  29\n  30\n\nJuly\nSun Mon Tue Wed Thu Fri Sat\n       1   2   3   4   5   6\n   7   8   9  10  11  12  13\n  14  15  16  17  18  19  20\n  21  22  23  24  25  26  27\n  28  29  30  31\n\nAugust\nSun Mon Tue Wed Thu Fri Sat\n                   1   2   3\n   4   5   6   7   8   9  10\n  11  12  13  14  15  16  17\n  18  19  20  21  22  23  24\n  25  26  27  28  29  30  31\n\nSeptember\nSun Mon Tue Wed Thu Fri Sat\n   1   2   3   4   5   6   7\n   8   9  10  11  12  13  14\n  15  16  17  18  19  20  21\n  22  23  24  25  26  27  28\n  29  30\n\nOctober\nSun Mon Tue Wed Thu Fri Sat\n           1   2   3   4   5\n   6   7   8   9  10  11  12\n  13  14  15  16  17  18  19\n  20  21  22  23  24  25  26\n  27  28  29  30  31\n\nNovember\nSun Mon Tue Wed Thu Fri Sat\n                       1   2\n   3   4   5   6   7   8   9\n  10  11  12  13  14  15  16\n  17  18  19  20  21  22  23\n  24  25  26  27  28  29  30\n\nDecember\nSun Mon Tue Wed Thu Fri Sat\n   1   2   3   4   5   6   7\n   8   9  10  11  12  13  14\n  15  16  17  18  19  20  21\n  22  23  24  25  26  27  28\n  29  30  31"}]'::jsonb, '', '[]'::jsonb, '[8]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'year = int(input())

month_names = ["January", "February", "March", "April", "May", "June",
               "July", "August", "September", "October", "November", "December"]

def is_leap_year(y):
    return (y % 4 == 0 and y % 100 != 0) or (y % 400 == 0)

def days_in_month(y, m):
    if m in [1, 3, 5, 7, 8, 10, 12]:
        return 31
    elif m in [4, 6, 9, 11]:
        return 30
    else:
        return 29 if is_leap_year(y) else 28

def day_of_week(y, m):
    t = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4]
    if m < 3:
        y -= 1
    return (y + y // 4 - y // 100 + y // 400 + t[m - 1] + 1) % 7

print(year)
for month in range(1, 13):
    print(month_names[month - 1])
    print("Sun Mon Tue Wed Thu Fri Sat")
    start = day_of_week(year, month)
    line = "    " * start
    for day in range(1, days_in_month(year, month) + 1):
        line += f"{day:4}"
        if (start + day) % 7 == 0:
            print(line.rstrip())
            line = ""
    if line.strip():
        print(line.rstrip())
    if month != 12:
        print()', '本题是顺序结构的典型应用，需要学生综合运用数据输入、运算处理和结果输出三个环节。学生需要：1）正确输入年份；2）计算闰年和月份天数；3）计算每月第一天是星期几；4）格式化输出整齐的年历。这个练习帮助学生理解程序自上而下执行的流程，培养逻辑思维和问题分解能力。', '["闰年判断逻辑错误，特别是忽略能被400整除也是闰年的规则","没有正确计算每月第一天的星期位置，导致日期对齐错误","2月份天数在闰年时未正确设置为29天","输出格式不整齐，日期宽度不一致或分隔不正确"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (491, 43, 'PY43-KC-034', '举例：用户登录', '编写程序，实现一个用户登录系统。

预设正确的用户名为 `admin`，密码为 `123456`。

从键盘输入用户名和密码，先判断用户名是否正确：
- 如果用户名不正确，则直接提示 "用户名输入有误"
- 如果用户名正确，则进一步判断密码：
  - 密码正确时输出 "登录成功"
  - 密码错误时输出 "密码错误"', '两行字符串，第一行是用户名，第二行是密码', '根据登录验证结果输出一行提示信息：
- 用户名错误时输出 "用户名输入有误"
- 密码正确时输出 "登录成功"
- 密码错误时输出 "密码错误"', 'Low', '[{"input": "admin\n123456", "output": "登录成功"}, {"input": "admin\nwrong", "output": "密码错误"}, {"input": "user\n123456", "output": "用户名输入有误"}]'::jsonb, '', '[]'::jsonb, '[37,38,39]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 预设正确的用户名和密码
correct_username = "admin"
correct_password = "123456"

# 从键盘输入用户名和密码
username = input()
password = input()

# 使用嵌套分支判断登录结果
if username == correct_username:
    # 用户名正确，进一步判断密码
    if password == correct_password:
        print("登录成功")
    else:
        print("密码错误")
else:
    # 用户名错误
    print("用户名输入有误")', '本题是分支语句嵌套的经典应用场景。通过用户登录验证的例子，帮助初学者理解多层条件判断的逻辑结构——先进行外层条件判断（用户名是否正确），只有在满足外层条件后才会进入内层判断（密码是否正确）。这种嵌套结构广泛应用于需要递进判断的实际问题中。', '["直接使用两个独立的 if 语句判断用户名和密码，导致即使用户名错误也会继续判断密码","将判断顺序搞反，先判断密码再判断用户名","输出信息与题目要求不完全匹配，如输出 \"用户名错误\" 而非 \"用户名输入有误\""]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (492, 43, 'PY43-KC-033', '举例：考试成绩判断', '编写一个程序，根据给定的考试成绩输出对应的等级。

评分标准如下：
- 分数大于或等于90分，输出"优秀"
- 分数小于90且大于或等于75分，输出"良好"
- 分数小于75且大于或等于60分，输出"及格"
- 分数小于60分，输出"不及格"', '输入一个整数分数score（0到100之间的整数）', '输出对应的等级字符串："优秀"、"良好"、"及格"或"不及格"', 'Low', '[{"input": "85", "output": "良好"}, {"input": "92", "output": "优秀"}, {"input": "60", "output": "及格"}]'::jsonb, '', '[]'::jsonb, '[15]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 读取分数
score = int(input())

# 根据分数判断等级
if score >= 90:
    print("优秀")
elif score >= 75:
    print("良好")
elif score >= 60:
    print("及格")
else:
    print("不及格")', '本题是分支结构的典型应用，通过多条件判断将分数划分为四个等级。练习if-elif-else多分支语句的使用，理解条件判断的逻辑顺序（从高到低逐级判断）。这是编程中最基础也是最实用的控制结构之一。', '["条件顺序错误：没有从高到低判断，导致分数90被\"良好\"分支捕获","边界值判断错误：使用了>而不是>=，导致90分无法输出\"优秀\"","忘记处理所有情况：缺少else分支处理不及格的情况"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (493, 43, 'PY43-KC-031', '使用for语句计算自然数之和', '编写程序，使用for语句计算1～n的自然数之和。

**示例说明**：
假设输入n=10，则计算1+2+3+4+5+6+7+8+9+10的结果并输出。

**提示**：利用for循环和range()函数可以方便地实现累加操作。', '输入一个正整数n（1 ≤ n ≤ 10000），表示需要求和的自然数上限。', '输出1到n所有自然数的和。', 'Low', '[{"input": "10", "output": "55"}, {"input": "100", "output": "5050"}]'::jsonb, '', '[]'::jsonb, '[51,52]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'n = int(input())
total = 0
for i in range(1, n + 1):
    total += i
print(total)', '本题帮助初学者理解for循环的基本用法，特别是与range()函数配合实现确定次数的循环操作。通过累加计算自然数之和的经典案例，让学习者掌握如何在循环中使用累加器变量，是理解循环控制结构的入门级练习。', '["range()函数的右边界是开区间，容易漏加最后一个数，应写成range(1, n+1)","累加变量需要先初始化为0","输出时忘记用print()函数或格式错误"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (494, 43, 'PY43-KC-034', '开发一个小型计算器', '编写程序，开发一个小型计算器。从键盘输入两个数字（可能为整数或小数）和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算。如果运算符不是 +、-、*、/ 之一，则输出错误提示信息。

**提示**：可以使用分支语句（if-elif-else）来处理不同的运算符。', '输入一行，包含两个数字（可能为整数或小数）和一个运算符，数字与运算符之间用空格分隔。例如：`3 4 +`', '如果运算符有效（+、-、*、/），输出运算结果，保留小数点后两位。如果运算符无效，输出 `Error`。注意：浮点数结果需保留两位小数（如 7.00，3.50）。', 'Low', '[{"input": "3 4 +", "output": "7.00"}, {"input": "10 3 -", "output": "7.00"}, {"input": "5 3 %", "output": "Error"}]'::jsonb, '', '[]'::jsonb, '[43]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'line = input()
parts = line.split()

a = float(parts[0])
b = float(parts[1])
op = parts[2]

if op == ''+'':
    result = a + b
elif op == ''-'':
    result = a - b
elif op == ''*'':
    result = a * b
elif op == ''/'':
    if b == 0:
        print("Error")
    else:
        result = a / b
        print("{:.2f}".format(result))
else:
    print("Error")

if op in [''+'', ''-'', ''*'']:
    print("{:.2f}".format(result))', '本题是分支语句嵌套的典型应用场景。通过 if-elif-else 结构处理多个运算符的分支判断，帮助初学者理解多条件分支的处理方式。题目还涉及浮点数输入、格式化输出以及除法运算，是综合练习分支结构的良好素材。', '["没有将输入的字符串转换为浮点数进行计算，导致整数除法结果不正确","输出时没有保留两位小数格式（如将 7.0 输出为 7 而不是 7.00）","没有处理除数为零的情况","运算符比较时使用了赋值运算符 = 而不是比较运算符 ==","输入分割时没有正确处理空格，导致索引错误"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (495, 43, 'PY43-KC-034', '小型计算器', '编写程序，开发一个小型计算器。从键盘输入两个数字和一个运算符，根据运算符（+、-、*、/）进行相应的数学运算，如果输入的运算符不是这4种，则给出错误提示。', '输入包含三行：第一行是一个浮点数（被操作数），第二行是一个浮点数（操作数），第三行是一个字符（运算符，取值为 +、-、*、/ 之一，或其他字符）', '输出为一行：若运算符为 +、-、*、/ 之一，输出两数运算的结果；若运算符非法，输出错误提示信息', 'Low', '[{"input": "3\n2\n+", "output": "5.0"}, {"input": "5\n3\n%", "output": "错误：运算符非法"}]'::jsonb, '', '[]'::jsonb, '[43]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'a = float(input())
b = float(input())
op = input()

if op == ''+'':
    result = a + b
    print(result)
elif op == ''-'':
    result = a - b
    print(result)
elif op == ''*'':
    result = a * b
    print(result)
elif op == ''/'':
    result = a / b
    print(result)
else:
    print(''错误：运算符非法'')', '本题是分支语句嵌套的典型应用，要求学生使用 if-elif-else 结构处理多条件判断。通过构建一个小型计算器，学生需要判断运算符是否合法，并分别执行相应的算术运算。这训练了学生对分支结构的理解和多条件判断的能力。', '["忘记处理非法运算符的情况，直接进行运算导致报错","使用 == 比较字符串时未注意大小写问题","除法运算使用了整数除法 // 而非浮点数除法 /"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (496, 43, 'PY43-KC-034', '猜数字游戏', '编写程序，调用随机函数生成一个1～10之间的随机整数，从键盘输入数字进行猜谜游戏，给出猜测结果（太大、太小、成功）的提示。

程序会预先知道秘密数字（第一个输入），玩家输入猜测数字（第二个输入），程序比较后输出相应的提示。', '输入包含一行，两个整数，用空格分隔。第一个整数是秘密数字（1～10之间），第二个整数是玩家猜测的数字。', '输出一行一个字符串：
- 如果猜测大于秘密数字，输出"太大"
- 如果猜测小于秘密数字，输出"太小"
- 如果猜测等于秘密数字，输出"成功"', 'Low', '[{"input": "5 7", "output": "太大"}, {"input": "3 1", "output": "太小"}, {"input": "8 8", "output": "成功"}]'::jsonb, '', '[]'::jsonb, '[45]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'data = input().split()
secret = int(data[0])
guess = int(data[1])
if guess > secret:
    print("太大")
elif guess < secret:
    print("太小")
else:
    print("成功")
', '本题是分支语句嵌套的经典应用场景。程序需要根据猜测数字与秘密数字的比较结果，输出三种不同的提示信息。通过if-elif-else结构实现多层次的条件判断，帮助初学者理解分支嵌套的逻辑流程。猜数字游戏融合了随机数生成、用户输入和条件判断三个基本编程概念。', '["输出的中文字符串写错，如写成\"太大了\"或\"toodaxia\"","比较条件写反了，把大于判断成太小，小于判断成太大","没有处理等于的情况，只写了if和else两种分支"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (497, 43, 'PY43-KC-041', '举例：成绩统计 V1（A B F 字典统计）', '某班级学生成绩分别为 `["A", "A", "B", "B", "F", "F", "B", "A", "B", "F"]`，使用字典统计成绩的分布情况，输出每个成绩等级的个数。

**提示**：字典的键用于存储统计项（成绩等级），字典的值用于存储次数（该等级的个数）。', '输入为一个包含成绩等级的字符串， grades 之间用空格分隔。例如：`A A B B F F B A B F`', '输出为每个成绩等级及其个数的统计结果，每行一个成绩等级，格式为：`成绩 个数`

例如输出：
```
A 3
B 4
F 3
```', 'Low', '[{"input": "A A B B F F B A B F", "output": "A 3\nB 4\nF 3"}]'::jsonb, '', '[]'::jsonb, '[93,94]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', '# 读取输入的成绩字符串
line = input()
grades = line.split()

# 初始化一个空字典用于统计
result = {}

# 遍历成绩列表，统计每个成绩等级的个数
for grade in grades:
    if grade in result:
        result[grade] += 1
    else:
        result[grade] = 1

# 输出统计结果
# 按字母顺序输出每个成绩等级及其个数
for grade in sorted(result.keys()):
    print(grade, result[grade])', '本题是字典在实际问题中应用的经典入门案例。通过统计班级学生成绩的分布情况，让学生理解字典这一数据结构的核心用途：使用键（成绩等级）快速存储和查找对应的值（该等级的个数）。这种统计模式在数据分析、信息检索等场景中非常常见，是后续学习更复杂数据处理的基础。', '["在向字典添加新键时忘记初始化，导致 KeyError","未处理输入中的空格或换行符，导致成绩列表为空","输出格式不正确，如忘记按字母顺序输出或格式不匹配","未考虑边界情况，如只有一个成绩或所有成绩都相同的情况"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (498, 43, 'PY43-KC-041', '举例：成绩统计 V2（带格式化输出）', '给定一串成绩记录，统计每种成绩出现的次数。成绩只包含大写字母 A、B、F，分别代表优秀、良好、不及格。程序需要统计并按字母顺序逐行输出每种成绩及其出现次数。', '一行字符串，包含若干个用空格分隔的成绩字母（只含 A、B、F），例如："A A B B F F B A B F"', '逐行打印每个成绩及其出现次数，格式为"{成绩}有{数量}个"，按成绩字母顺序输出。例如对于 "A A B B F F B A B F"，输出为：
A有3个
B有4个
F有3个', 'Low', '[{"input": "A A B B F F B A B F", "output": "A有3个\nB有4个\nF有3个"}]'::jsonb, '', '[]'::jsonb, '[94,95,96]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

line = sys.stdin.read().strip()
scores = line.split()

score_dict = {}
for score in scores:
    if score in score_dict:
        score_dict[score] += 1
    else:
        score_dict[score] = 1

for score in sorted(score_dict.keys()):
    print(f"{score}有{score_dict[score]}个")', '本题是字典应用的经典入门示例，帮助初学者理解如何用字典存储键值对并实现计数功能。通过遍历列表并更新字典，学生可以掌握字典的基本操作（创建、添加、查找），以及字典与列表的区别。这是后续学习数据统计和信息管理问题的基础。', '["忘记初始化字典就直接进行计数操作","输出时顺序不确定（应使用 sorted() 确保按字母顺序输出）","没有处理只出现一次的字母，输出格式不完整"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (499, 43, 'PY43-KC-041', '手机通讯录管理', '实现一个基于字典的手机通讯录管理系统，支持以下功能：
1. 添加联系人：输入姓名和电话存入字典
2. 删除联系人：从字典中删除指定联系人
3. 查找联系人：根据姓名查找电话
4. 显示电话本：遍历并打印所有联系人
0. 退出程序

程序通过菜单选择操作类型，使用while循环配合分支语句构建程序框架。', '程序运行时通过交互式菜单接收用户选择和联系人信息，包括：(1)菜单选项数字(0-4)；(2)联系人姓名；(3)联系人电话。每个输入占一行，程序按顺序处理直到遇到0退出。', '根据用户操作输出相应结果：
- 添加成功："联系人XXX已成功保存到通讯录!!"
- 删除成功："联系人XXX已删除!!"
- 查找成功："联系人XXX的电话为：YYY"
- 显示电话本：逐行打印"姓名:电话"
- 操作失败："联系人XXX不存在!!"
- 退出：程序结束', 'Low', '[{"input": "1\nAlice\n123456\n4\n0", "output": "联系人Alice已成功保存到通讯录!!\nAlice:123456"}]'::jsonb, '', '[]'::jsonb, '[97,98,99,100,101,102,103,104,105]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'contact_dict = {}
choice = input()
while choice != "0":
    if choice == "1":
        name = input()
        phone = input()
        contact_dict[name] = phone
        print("联系人{}已成功保存到通讯录!!".format(name))
    elif choice == "2":
        name = input()
        if name in contact_dict:
            del contact_dict[name]
            print("联系人{}已删除!!".format(name))
        else:
            print("联系人{}不存在!!".format(name))
    elif choice == "3":
        name = input()
        if name in contact_dict:
            print("联系人{}的电话为：{}".format(name, contact_dict[name]))
        else:
            print("联系人{}不存在!!".format(name))
    elif choice == "4":
        for name, phone in contact_dict.items():
            print("{}:{}".format(name, phone))
    else:
        print("指令输入错误，请重新输入！")
    choice = input()', '本问题通过实现一个完整的手机通讯录管理系统，帮助初学者掌握字典在实际问题中的应用。字典作为Python中重要的数据结构，适合用于需要键值对存储和快速查找的场景，如通讯录、成绩统计等。通过本问题，学生将学会：1）使用字典存储联系人信息；2）实现添加、删除、查找、遍历等字典基本操作；3）使用while循环和分支语句构建菜单驱动的程序框架。这是从理论学习到实践应用的重要过渡。', '["直接访问字典键而未先检查键是否存在，导致KeyError异常","使用input()直接与数字比较时未注意类型（input返回字符串）","删除或查找前忘记使用in运算符检查键是否存在","遍历字典时使用错误的迭代方式，应使用items()同时获取键和值"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (500, 43, 'PY43-KC-047', '举例：统计需要取快递人员的名单', '大学城每天都能收到很多快递。请为快递员写一个程序统计一下收到快递的人员名单，以便统一通知。

程序循环接收用户输入的收货人姓名，将每个姓名加入集合中，当输入 `EXIT` 时退出循环，并打印所有已录入的收货人名单。

**注意**：由于使用集合存储，同一姓名即使被多次输入也只保留一份。', '多行输入，每行一个收货人姓名，以单独一行 "EXIT" 结束输入。', '先输出"结束录入！"，再输出"所有收货人名单如下："，最后按任意顺序输出所有已录入的收货人姓名（每行一个）。', 'Low', '[{"input": "张三\n李四\n王五\nEXIT", "output": "结束录入！\n所有收货人名单如下：\n张三\n李四\n王五"}]'::jsonb, '', '[]'::jsonb, '[64,65]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'contact_set = set()
while True:
    name = input()
    if name == "EXIT":
        print("结束录入！")
        print("所有收货人名单如下：")
        for contact in sorted(contact_set):
            print(contact)
        break
    contact_set.add(name)
', '本题是集合类型的经典入门练习。通过循环读取用户输入并将数据存入集合，直观展示集合的两个核心特性：无序性和元素唯一性。收货人名单天然适合用集合存储——顺序无关紧要，且同一个人不会重复通知。本题帮助学生理解如何用 `set()` 创建集合、如何用 `add()` 添加元素、以及何时适合选用集合而非列表。', '["在输入 EXIT 后仍然尝试将 \"EXIT\" 加入集合，应先判断再决定是否添加。","忘记在输入非 EXIT 时调用 `contact_set.add(name)` 将姓名加入集合。","输出时没有按题目要求的固定顺序输出 \"结束录入！\" 和 \"所有收货人名单如下：\" 两行引导文字。","重复输入的姓名在输出中出现多次——未理解集合的去重特性，导致输出包含重复项。"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (501, 43, 'PY43-KC-047', '交集', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），输出两个集合的交集，即两个人都喜欢的地点。集合元素为字符串。

**提示**：可以使用集合的 `intersection()` 方法或 `&` 运算符来求交集。', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为两个集合的交集（使用intersection或&运算符），即同时存在于两个集合中的元素。输出格式为Python集合表示法，如 {元素1,元素2}，元素之间用逗号分隔。如果交集为空，输出 set()。', 'Low', '[{"input": "天水,淄博\n哈尔滨,天水", "output": "{''天水''}"}]'::jsonb, '', '[]'::jsonb, '[50]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def format_set(s):
    if not s:
        return "set()"
    return "{" + ", ".join(repr(x) for x in sorted(s)) + "}"


line1 = input().strip()
line2 = input().strip()
set1 = set(line1.split('',''))
set2 = set(line2.split('',''))
print(format_set(set1 & set2))
', '集合的交集运算是Python集合类型的基本操作之一。本问题让初学者理解：1) 如何将逗号分隔的字符串转换为集合；2) 如何使用 intersection() 方法或 & 运算符求交集；3) 集合的无序性和元素唯一性特点。这是后续学习集合其他运算（并集、差集、补集）的基础。', '["混淆字符串和集合：需要先将逗号分隔的字符串用 split('','') 拆分成列表，再用 set() 转换为集合","输出格式错误：输出应该是Python集合表示法，元素用逗号分隔，外层用大括号包裹；空集合应输出 set() 而不是 {}"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (502, 43, 'PY43-KC-047', '差集', '## 题目描述

编写一个程序，接收两组字符串形式的地点集合（每行使用逗号分隔），输出这两个集合的差集结果。
- 第一行输出第一个集合中独有的元素（即第一个集合减去第二个集合的差集）。
- 第二行输出第二个集合中独有的元素（即第二个集合减去第一个集合的差集）。

集合元素为字符串，输出时请使用 Python 的集合表示形式（如 `{''北京'', ''上海''}`），空集合输出为 `set()`。

## 输入格式

两行输入，每行包含若干用逗号分隔的字符串元素，表示两个集合的元素。

## 输出格式

两行输出：
1. 第一个集合减去第二个集合的差集。
2. 第二个集合减去第一个集合的差集。

（请使用 Python 集合的字面量形式输出）', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '两行输出，第一行为第一个集合减去第二个集合后的结果（使用difference或-运算符），第二行为第二个集合减去第一个集合后的结果。', 'Low', '[{"input": "天水,淄博\n哈尔滨,天水", "output": "{''淄博''}\n{''哈尔滨''}"}, {"input": "a,b,c\nc,b,a", "output": "set()\nset()"}]'::jsonb, '', '[]'::jsonb, '[49]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def format_set(s):
    if not s:
        return "set()"
    return "{" + ", ".join(repr(x) for x in sorted(s)) + "}"


def parse_set(line):
    if line.strip() == "":
        return set()
    items = [item.strip() for item in line.split(",")]
    return set(item for item in items if item)


line1 = input().rstrip("\n")
line2 = input().rstrip("\n")
set1 = parse_set(line1)
set2 = parse_set(line2)
print(format_set(set1 - set2))
print(format_set(set2 - set1))
', '本题要求学生掌握 Python 集合类型的基本概念及其运算操作，特别是集合的差集运算（`-` 或 `difference`）。通过实际处理两组字符串集合，学生可以体会集合的‘唯一性’与‘无序性’，并学习如何在程序中实现数据的去重与比较。该题目是后续学习交集、并集等集合运算的基础，也是处理去重、唯一标识等实际问题的常用技巧。', '["忘记把输入的字符串转换为集合，直接在列表上进行差运算导致结果错误。","未去除元素两侧的空格，导致集合中出现 `\" 天水\"` 与 `\"天水\"` 被视为不同元素。","输出时使用 `print(set1 - set2)` 而不是 `print(set1 - set2)`，但忘记处理空集的情况（空集应输出 `set()`）。","误用列表的减法（如 `list1 - list2`）而期待得到差集，Python 列表不支持减法运算符。","在输出集合时，使用 `print(*sorted_set)` 等方式导致输出缺少花括号或元素顺序不符合预期。"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (503, 43, 'PY43-KC-047', '补集', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），输出两个集合的对称差集，即只被其中一个集合包含而不在另一个集合中的元素。集合元素为字符串。

**提示**：对称差集可以使用 `set1.symmetric_difference(set2)` 或 `set1 ^ set2` 计算。', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为两个集合的对称差集（使用symmetric_difference或^运算符），即只出现在一个集合中的元素。', 'Low', '[{"input": "天水,淄博\n哈尔滨,天水", "output": "{''哈尔滨'', ''淄博''}"}]'::jsonb, '', '[]'::jsonb, '[51]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def format_set(s):
    if not s:
        return "set()"
    return "{" + ", ".join(repr(x) for x in sorted(s)) + "}"


line1 = input().strip()
line2 = input().strip()
set1 = set(line1.split('',''))
set2 = set(line2.split('',''))
print(format_set(set1 ^ set2))
', '本题帮助初学者理解Python集合类型中的对称差集（补集）运算。对称差集返回的是只出现在一个集合而不出现在另一个集合中的元素，这是集合论中的基础概念。通过实际处理字符串集合（如地点集合），学生可以掌握集合的创建、交集/并集/差集/对称差集的运算方法，以及set类型的实际应用场景。', '["将输入的字符串直接作为集合处理，忘记用split()分割后再转为set","误用交集（&）或普通差集（-）而非对称差集（^）","输出时没有将结果转换为set格式","忽略输入中可能存在的空字符串或空格"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (504, 43, 'PY43-KC-047', '集合类型的复合赋值操作符', '编写程序，接收两组字符串形式的地点集合（用逗号分隔），使用复合赋值操作符 `symmetric_difference_update` 将第一个集合更新为两个集合的对称差集，然后输出更新后的第一个集合。集合元素为字符串。

**对称差集**：指同时属于两个集合但不属于两者交集的元素，即只出现在一个集合中的元素。

**示例**：
- 集合1: {"天水", "淄博"}
- 集合2: {"哈尔滨", "天水"}
- 对称差集结果: {"淄博", "哈尔滨"}（"天水"在两个集合中都出现，因此被排除）', '两行输入，每行包含逗号分隔的字符串元素，表示两个集合的元素。', '一行输出，为使用 symmetric_difference_update 更新后的第一个集合（即只出现在一个集合中的元素）。', 'Low', '[{"input": "天水,淄博\n哈尔滨,天水", "output": "{''哈尔滨'', ''淄博''}"}]'::jsonb, '', '[]'::jsonb, '[52]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def format_set(s):
    if not s:
        return "set()"
    return "{" + ", ".join(repr(x) for x in sorted(s)) + "}"


line1 = input().strip()
line2 = input().strip()
set1 = set(line1.split('',''))
set2 = set(line2.split('',''))
set1.symmetric_difference_update(set2)
print(format_set(set1))
', '本题练习集合的复合赋值操作符 symmetric_difference_update，该操作符将集合更新为自身的对称差集。相比普通操作符（如 ^），复合赋值操作符（如下划线后缀版本）会直接修改原集合，而不是返回新集合。这是Python中常见的原地更新模式，在处理大型数据集时可以节省内存。', '["将 symmetric_difference_update 与 symmetric_difference 混淆，前者会修改原集合，后者返回新集合","忘记将输入的逗号分隔字符串转换为集合，直接对字符串进行集合操作","误用其他集合操作符（如 union_update、intersection_update），导致结果错误","输出时没有正确处理集合的字符串表示格式"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (505, 43, 'PY43-KC-053', '6.36 词频统计问题', '## 词频统计问题

### 题目描述

给定一篇文章，统计其中共出现了多少个词，以及每个词出现的次数。

### 基本要求

1. 读取输入的文章内容
2. 统计文章中出现的词语总数
3. 统计每个词出现的次数
4. 按出现次数从多到少输出词频统计结果

### 提示

- 词与词之间以空白字符（空格、换行、制表符）分隔
- 可以使用字典（dict）来统计每个词的出现次数
- 使用 sorted() 函数配合 key 参数实现按出现次数降序排列', '一篇文章的完整文本内容，词与词之间以空白字符分隔', '第一行输出词的总数量，第二行按出现次数从多到少输出每个词及其出现次数，格式为：词:次数（词与词之间用空格分隔）', 'Low', '[{"input": "hello world hello", "output": "3\nhello:2 world:1"}, {"input": "Python Python Java Python", "output": "4\nPython:3 Java:1"}, {"input": "one two three", "output": "3\none:1 three:1 two:1"}]'::jsonb, '', '[]'::jsonb, '[36,37]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

def solve():
    text = sys.stdin.read().strip()
    if not text:
        return
    
    words = text.split()
    
    freq = {}
    for word in words:
        freq[word] = freq.get(word, 0) + 1
    
    total = len(words)
    print(total)
    
    sorted_words = sorted(freq.items(), key=lambda x: (-x[1], x[0]))
    result = '' ''.join(f''{word}:{count}'' for word, count in sorted_words)
    print(result)

solve()', '词频统计是数据分析的基础技能，通过本问题可以学习如何使用字典作为计数器存储单词出现次数，掌握字典的get()方法优化计数逻辑，将字典转换为列表进行排序，以及使用lambda函数配合sort()方法实现按值降序排列。这是Python文件操作和数据分析章节的综合性练习。', '["直接输出字典而不是按要求格式输出词:次数","没有按出现次数从高到低排序","计算总词数时出错，应统计所有单词的数量而非不同单词的数量"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (506, 43, 'PY43-KC-053', '6.41 关键步骤3：对单词的统计值从高到底排序', '实现一个函数，对给定的词频统计字典进行排序。

本问题要求读取单词及其出现次数，然后按照出现次数从高到低输出排序后的结果。这是词频统计分析中的关键步骤，将无序的字典数据转换为有序的列表以便后续处理。', '第一行整数n表示词数（1≤n≤1000），接下来n行每行一个单词及其出现次数，单词为不含空格的字符串，次数为正整数。', '按出现次数从高到低输出所有单词及次数，每行一个，格式为「单词 次数」。', 'Low', '[{"input": "4\nthe 1138\ntragedy 3\nof 669\nhamlet 462", "output": "the 1138\nof 669\nhamlet 462\ntragedy 3"}]'::jsonb, '', '[]'::jsonb, '[41]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'n = int(input())
items = []
for _ in range(n):
    line = input().strip()
    word, count = line.rsplit('' '', 1)
    items.append((word, int(count)))

items.sort(key=lambda x: x[1], reverse=True)

for word, count in items:
    print(f"{word} {count}")', '本问题是词频统计分析的核心步骤之一。在实际文本分析中，我们经常需要知道哪些词出现得最频繁。通过将字典转换为列表并按值排序，可以快速得到按出现次数降序排列的结果。这个技能不仅用于词频统计，在任何需要按数值排序数据的场景下都很实用，如成绩排名、销售数据分析等。', '["忘记使用 reverse=True 导致结果按升序排列而非降序","lambda函数的key参数写错，如写成 lambda x:x[0] 而非 lambda x:x[1]","未将字典转换为列表就直接调用sort方法，因为字典本身没有sort方法","排序后输出格式错误，忘记用空格分隔单词和次数"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (507, 43, 'PY43-KC-049', '筛选出租车位置数据', '给定一个CSV格式的数据文件（无标题行），每行包含时间、车牌号、北纬、东经四个字段，字段之间以英文逗号分隔。编写程序读取该文件，筛选出位于指定地理区域内的车辆记录并打印输出。

筛选条件：
- 北纬范围：31.2222 ~ 31.2333（含边界）
- 东经范围：121.45 ~ 121.55（含边界）', '输入为CSV格式的车辆位置数据，无标题行。每行格式为：时间,车牌号,北纬,东经。
例如：
13:30,沪A12345,31.2280,121.5000
10:00,沪B67890,31.2200,121.4600
15:45,沪C22222,31.2400,121.5200', '打印所有满足筛选条件的车辆记录，每条记录占一行，格式与输入相同。
筛选条件：31.2222 <= 北纬 <= 31.2333 且 121.45 <= 东经 <= 121.55
如果没有符合条件的记录，则输出"No records found."', 'Mid', '[{"input": "13:30,沪A12345,31.2280,121.5000\n14:15,沪B67890,31.2300,121.4800\n10:00,沪C11111,31.2200,121.4600\n15:45,沪D22222,31.2400,121.5200\n12:30,沪E33333,31.2250,121.5400", "output": "13:30,沪A12345,31.2280,121.5000\n14:15,沪B67890,31.2300,121.4800\n12:30,沪E33333,31.2250,121.5400"}]'::jsonb, '', '[]'::jsonb, '[49,50]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

def main():
    # 读取所有行
    lines = sys.stdin.read().strip().split(''\n'')
    
    results = []
    for line in lines:
        if not line.strip():
            continue
        parts = line.split('','')
        if len(parts) != 4:
            continue
        time_val, plate, lat_str, lon_str = parts
        latitude = float(lat_str)
        longitude = float(lon_str)
        
        # 判断是否在指定区域内（包含边界）
        if 31.2222 <= latitude <= 31.2333 and 121.45 <= longitude <= 121.55:
            results.append(line)
    
    # 输出结果
    if results:
        for record in results:
            print(record)
    else:
        print("No records found.")

if __name__ == "__main__":
    main()', '本题要求学生从CSV格式文件中读取数据，理解文件结构和字段含义，掌握字符串分割和数据解析方法。核心是理解地理坐标的边界条件判断，并能够根据多个条件进行数据筛选。通过处理出租车定位数据，让学生体会文件数据分析在实际场景中的应用价值。', '["将坐标边界条件写错，例如只判断大于而不包含等于，或混淆经度和纬度的边界值","没有处理空行或格式不规范的行导致程序报错","浮点数比较时直接用==导致精度问题，应使用区间判断","输出格式与输入格式不一致，漏写逗号或多余的空格"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (508, 43, 'PY43-KC-049', '举例：统计学科等级水平', '根据考试成绩统计学科等级水平。

输入文件score.txt为制表符(\t)分隔的文本文件，无标题行，每行包含考号、程序设计成绩、生物成绩、科学成绩。

判定规则：
1. 生物和科学都达到60分且总分达到180分，为**及格**；
2. 每门课都达到85分且总分达到260分，为**优秀**；
3. 总分不到180分或有任意一门课不到60分，为**不及格**。

请读取学生成绩数据，输出每位学生的考号及对应等级，格式为：考号,等级', '多行文本，每行包含考号、程序设计成绩、生物成绩、科学成绩，用制表符(\t)分隔，无标题行。例如：S001\t100\t100\t100', '多行文本，每行格式为：考号,等级（及格/优秀/不及格）', 'Low', '[{"input": "S001\t100\t100\t100", "output": "S001,优秀"}]'::jsonb, '', '[]'::jsonb, '[46,47,48]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

def main():
    lines = []
    for line in sys.stdin:
        line = line.strip()
        if line:
            lines.append(line)
    
    results = []
    for line in lines:
        parts = line.split(''\t'')
        student_id = parts[0]
        programming = int(parts[1])
        biology = int(parts[2])
        science = int(parts[3])
        total = programming + biology + science
        
        if programming >= 85 and biology >= 85 and science >= 85 and total >= 260:
            level = "优秀"
        elif biology >= 60 and science >= 60 and total >= 180:
            level = "及格"
        else:
            level = "不及格"
        
        results.append(f"{student_id},{level}")
    
    print(''\n''.join(results))

if __name__ == "__main__":
    main()', '本题是典型的基于文件的数据分析任务，要求学生从结构化文本数据中读取学生成绩，根据多条复合条件进行等级判定。通过处理制表符分隔的数据，学生可以练习字符串分割、列表索引、整数转换等基本操作，同时理解多条件分支的逻辑顺序（先判断优秀，再判断及格，最后不及格）。', '["条件判断顺序错误：应先判断优秀再判断及格，否则可能将优秀误判为及格","忽略制表符分隔：直接用空格分割会导致数据解析错误","总分计算遗漏：需将三门成绩相加后再与阈值比较"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (509, 43, 'PY43-KC-053', '词频统计代码实现', '读取输入的文本内容，统计文件中各单词出现频率，输出出现次数最多的前10个单词及出现次数。

文本中可能包含逗号、冒号、句点等标点符号，在统计时需要去除。单词统计不区分大小写。', '输入为一段英文文本（可能包含标点符号），文本可能跨越多行。', '输出前10个最高频单词，每行格式为"word===count"（单词===出现次数），按出现次数从高到低排序。如果单词总数不足10个，则输出所有单词。', 'Low', '[{"input": "the sun is bright the sky is blue the sun shines bright", "output": "the===3\nbright===2\nis===2\nsun===2\nblue===1\nshines===1\nsky===1"}]'::jsonb, '', '[]'::jsonb, '[44]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

content = sys.stdin.read().lower()
for ch in ['','', '':'', ''.'']:
    content = content.replace(ch, '' '')

words = content.split()
counts = {}
for word in words:
    counts[word] = counts.get(word, 0) + 1

items = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
for word, count in items[:10]:
    print(f"{word}==={count}")
', '本题是词频统计的经典应用场景，综合训练了字符串处理（大小写转换、去除标点符号）、字典作为计数器的使用、列表排序等核心技能。通过完成本题，学生能够掌握使用字典的get()方法优化计数逻辑，以及使用lambda函数配合sort()方法实现按值降序排列。这在实际数据分析、文本挖掘等领域都有广泛应用。', '["忘记将文本转换为小写，导致大小写不同的同一单词被当作不同单词统计","没有去除标点符号，标点符号会被当作单词的一部分","排序时使用错误的key，导致按字母序而非次数排序","排序方向错误（升序而非降序）"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (510, 43, 'PY43-KC-064', '7.2.1、类和对象的创建', '设计一个简单的面向对象模拟程序。

小明家有一只猫和一只狗。小明打开门时，小狗和小猫分别用叫声欢迎他，然后小明给它们分别喂食。

要求：
1. 创建Dog类和Cat类
2. 每个类包含名称属性(name)和打招呼(greet)、吃东西(eat)的方法
3. 程序读取一个整数n，重复n次完整的事件序列
4. 输出顺序为：狗叫、猫叫、猫吃、狗吃

输出序列：
- 小狗汪汪叫
- 小猫喵喵叫
- 小猫吃猫粮
- 小狗吃骨头', '输入一个正整数n (1 ≤ n ≤ 10)，表示执行事件序列的次数。', '输出n次完整的事件序列。每次序列包含4行：
1. 小狗汪汪叫
2. 小猫喵喵叫
3. 小猫吃猫粮
4. 小狗吃骨头', 'Low', '[{"input": "1", "output": "小狗汪汪叫\n小猫喵喵叫\n小猫吃猫粮\n小狗吃骨头"}, {"input": "2", "output": "小狗汪汪叫\n小猫喵喵叫\n小猫吃猫粮\n小狗吃骨头\n小狗汪汪叫\n小猫喵喵叫\n小猫吃猫粮\n小狗吃骨头"}]'::jsonb, '', '[]'::jsonb, '[58]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'class Dog:
    def __init__(self, name):
        self.name = name
    
    def greet(self):
        print("小狗汪汪叫")
    
    def eat(self):
        print("小狗吃骨头")


class Cat:
    def __init__(self, name):
        self.name = name
    
    def greet(self):
        print("小猫喵喵叫")
    
    def eat(self):
        print("小猫吃猫粮")


n = int(input())

dog = Dog("小狗")
cat = Cat("小猫")

for _ in range(n):
    dog.greet()
    cat.greet()
    cat.eat()
    dog.eat()', '本题帮助初学者理解面向对象编程的基本概念。通过创建Dog和Cat类，学生需要掌握：1) 如何定义类包含属性和方法；2) 如何创建类的实例（对象）；3) 如何调用对象的方法。本题通过一个生活化的情景（宠物迎接主人并进食），将抽象的类与对象概念具体化。', '["忘记在方法中使用print语句输出内容","输出顺序错误，将狗叫放在最后而不是开头","没有正确使用input()读取输入，导致程序无法处理多组测试数据","类的方法没有正确接收self参数"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (511, 43, 'PY43-KC-061', '计算阶乘', '请编写一个递归函数来计算阶乘。

阶乘（Factorial）是递归的经典例子。一个数的阶乘是该数与所有小于它的正整数的乘积。通常用符号 n! 表示。

**定义：**
n! = n × (n-1) × (n-2) × ... × 1

**递归表达：**
- 基准情形：0! = 1（任何数的阶乘都可以递归到 0!）
- 递归情形：n! = n × (n-1)!', '一个非负整数 n（0 ≤ n ≤ 20）', '整数 n 的阶乘值 n!', 'Low', '[{"input": "0", "output": "1"}, {"input": "5", "output": "120"}, {"input": "10", "output": "3628800"}]'::jsonb, '', '[]'::jsonb, '[54,55]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def factorial(n):
    if n == 0:
        return 1
    return n * factorial(n - 1)

n = int(input())
print(factorial(n))', '阶乘是理解递归最经典的入门例子。通过计算阶乘，初学者可以深刻理解递归的两大要素：基准情形（递归终止条件）和递归情形（规模缩小）。这道题帮助学生掌握如何将数学递归定义转化为代码实现，为后续解决更复杂的递归问题打下基础。', '["忘记基准情形（base case），导致无限递归","递归调用参数没有缩小规模，如写成 factorial(n) 调用 factorial(n)","没有正确处理 0 的边界情况","return 语句缺少递归调用的结果"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (512, 43, 'PY43-KC-059', '示例：定义一个接收任意数量关键字参数并打印的函数', '定义一个函数，使用 **kwargs 接收任意数量的关键字参数，将这些参数按 key=value 的格式打印出来。

请编写程序，从标准输入读取若干形如 key=value 的关键字参数（参数数量不定），然后按顺序打印每个关键字参数，格式为 key=value，每个参数占一行。', '输入若干行，每行包含一个形如 key=value 的关键字参数，参数名和值均为字符串，参数数量不定。', '按顺序打印每个关键字参数，格式为 key=value，每个参数占一行。', 'Low', '[{"input": "name=Alice\\nage=30", "output": "name=Alice\\nage=30"}, {"input": "x=10\\ny=20\\nz=30", "output": "x=10\\ny=20\\nz=30"}, {"input": "a=hello\\nb=world", "output": "a=hello\\nb=world"}]'::jsonb, '', '[]'::jsonb, '[49]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

def print_kwargs(**kwargs):
    for key, value in kwargs.items():
        print(f"{key}={value}")

# 读取所有输入行
lines = sys.stdin.read().strip().split(''\n'')

# 将输入转换为关键字参数并调用函数
kwargs = {}
for line in lines:
    if line.strip():
        key, value = line.split(''='', 1)
        kwargs[key] = value

print_kwargs(**kwargs)', '本题练习Python函数中 **kwargs 可变关键字参数的用法。通过将函数参数写成 **kwargs 的形式，函数可以接收任意数量的关键字参数，这些参数会被组织成一个字典。本题将这个概念转化为实际的程序：读取若干 key=value 格式的输入，并按相同格式输出，帮助学生理解 **kwargs 的工作原理。', '["忘记使用 split(''='', 1) 分割，如果值中包含等号会被错误分割","没有处理空输入的情况","输出时每个 key=value 后没有换行","函数定义中使用了 *args 而不是 **kwargs"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (513, 43, 'PY43-KC-059', '示例：定义一个接收任意数量数字并求和的函数', '定义一个函数，使用 Python 的 `*args` 语法接收任意数量的位置参数（数字），返回所有参数的和。然后读取标准输入中的 N 个整数，输出它们的和。

**提示**：利用 `*args` 可以让函数接受可变数量的参数，在函数内部 `args` 是一个元组。通过 `sum()` 函数可以方便地求和。', '第一行是一个整数 N（1≤N≤100），表示接下来有 N 个数字需要求和。
第二行包含 N 个整数，数字之间用空格分隔。', '输出这 N 个数字的和。', 'Low', '[{"input": "3\n1 2 3", "output": "6"}, {"input": "5\n1 2 3 4 5", "output": "15"}, {"input": "1\n100", "output": "100"}]'::jsonb, '', '[]'::jsonb, '[47]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'def sum_with_args(*args):
    """使用 *args 接收任意数量的位置参数，返回它们的和"""
    return sum(args)

# 读取输入
import sys
data = sys.stdin.read().strip().split()
if not data:
    exit()
n = int(data[0])
numbers = list(map(int, data[1:1+n]))

# 使用 * 解包列表，将元素作为独立参数传递给函数
result = sum_with_args(*numbers)
print(result)', '本题引导初学者掌握 Python 函数参数的高级用法——使用 `*args` 实现可变数量位置参数的接收。通过将可变参数与标准输入输出结合，让学习者理解 `*args` 在实际编程中的典型应用场景：处理未知数量的输入数据。', '["在函数定义时忘记使用 `*args`，导致无法接收多个参数","在调用函数时忘记使用 `*` 解包列表/元组传递参数","混淆 `*args`（可变位置参数）和 `**kwargs`（可变关键字参数）的使用场景"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;
INSERT INTO course_problem (id, pack_id, kc_code, title, statement_markdown, input_description, output_description, difficulty, samples, expected_output_pattern, hidden_test_cases, page_refs, source_label, reference_solution_language, reference_solution_code, teaching_explanation, common_mistakes) VALUES (514, 43, 'PY43-KC-064', '面向过程的编程', '某动物园里有几只小动物，它们会做各种动作。请定义三个函数来模拟这些行为：

1. **opendoor(name)**：输出 `{name}打开门`
2. **bark(name, voice)**：输出 `{name}发出{voice}的声音`
3. **eat(name, food)**：输出 `{name}吃{food}`

程序需要读取多行输入，每行是一条命令，格式为 `函数名 参数1 [参数2]`。对于每条命令，调用对应的函数并输出结果。', '输入包含多行，每行是一条命令：
- `opendoor 姓名`：调用 opendoor 函数
- `bark 姓名 声音`：调用 bark 函数
- `eat 姓名 食物`：调用 eat 函数

每行命令以空格分隔，函数名和参数之间用一个或多个空格隔开。', '对于每条输入命令，输出对应的执行结果，每条结果占一行。', 'Low', '[{"input": "opendoor 小明\nbark 小狼 汪汪\nbark 小虎 喵喵\nbark 小虎 猫粮\nbark 小狼 骨头", "output": "小明打开门\n小狼发出汪汪的声音\n小虎发出喵喵的声音\n小虎发出猫粮的声音\n小狼发出骨头的声音"}]'::jsonb, '', '[]'::jsonb, '[59]'::jsonb, 'Language Pack: 1defa338-bf8a-425e-806a-17435ebc407a', 'Python3', 'import sys

def opendoor(name):
    print("{}打开门".format(name))

def bark(name, voice):
    print("{}发出{}的声音".format(name, voice))

def eat(name, food):
    print("{}吃{}".format(name, food))

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    parts = line.split()
    func = parts[0]
    if func == "opendoor":
        opendoor(parts[1])
    elif func == "bark":
        bark(parts[1], parts[2])
    elif func == "eat":
        eat(parts[1], parts[2])', '本题是面向过程编程的入门练习，通过定义三个不同参数的函数（opendoor 1个参数，bark和eat 2个参数），帮助学生理解：1) 函数定义的基本语法；2) 函数参数的作用；3) 如何通过函数调用实现不同行为。题目将原始hardcoded的函数调用转换为命令驱动的输入模式，保留核心的函数定义教学目标，同时满足OJ系统的stdin/stdout格式要求。', '["忘记使用 format() 或 f-string，直接 print 字符串字面量而非变量","输出格式错误，如 bark 函数应输出''的声音''而非''叫''或''叫了一声''","没有正确处理多参数函数的参数传递，导致参数错位","split() 后没有检查参数个数就直接使用 parts[1]、parts[2]"]'::jsonb) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;

-- count_check document=7
-- count_check page=561
-- count_check chapter=7
-- count_check kc=65
-- count_check example=46
-- count_check problem=39

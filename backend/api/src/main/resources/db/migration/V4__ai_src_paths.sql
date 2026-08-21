-- ---------------------------------------------------------------------------------------------
-- Store where the object is, not how to reach it.
--
-- `question_lib.ai_src` held fully-qualified URLs baked in at upload time. Two rows still point at
-- http://192.168.0.104:9010 - a LAN address this machine lost when the router reissued leases, so
-- those avatar clips have been dead links ever since. Twelve more point at http://127.0.0.1:9010,
-- which resolves on the desktop that wrote them and on nothing else: any phone loading the
-- interview page asks itself for the video.
--
-- The host is deployment configuration and changes without anyone editing a row; the object path
-- does not. MediaUrlResolver joins the two back together at read time, so this migration only has
-- to strip what should never have been persisted.
--
-- Matches any scheme://host[:port] prefix rather than the two known hosts, because the next stale
-- address will be a third one.
-- ---------------------------------------------------------------------------------------------

UPDATE `question_lib`
SET `ai_src` = SUBSTRING(`ai_src`, LOCATE('/', `ai_src`, LOCATE('://', `ai_src`) + 3))
WHERE `ai_src` LIKE '%://%'
  AND LOCATE('/', `ai_src`, LOCATE('://', `ai_src`) + 3) > 0;

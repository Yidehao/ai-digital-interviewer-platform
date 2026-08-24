<template>
  <div class="review-box">
    <el-alert type="info" :closable="false" show-icon
      title="These assessments are advisory. They do not decide anything."
      description="Grading is a model's opinion, measured against two human labelers at QWK 0.56 where the labelers agreed with each other at 0.97. It also docks a point of technical correctness for non-native phrasing, at every competence level, where neither human docked any. Read the transcript and form your own view. That is the point of this screen."
      style="margin-bottom: 16px;">
    </el-alert>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="Review Queue" name="queue">
        <div class="search-area">
          <el-checkbox v-model="unreviewedOnly" @change="load">Awaiting review only</el-checkbox>
          <el-button type="primary" size="middle" @click="load" style="margin-left: 12px;">Refresh</el-button>
        </div>

        <el-table :data="rows" border stripe empty-text="No interviews graded yet">
          <el-table-column type="index" width="50" align="center"></el-table-column>
          <el-table-column label="Session" width="180" align="center">
            <template slot-scope="s"><span class="mono">{{ s.row.sessionId.substring(0, 12) }}</span></template>
          </el-table-column>
          <el-table-column label="Candidate" width="200" align="center">
            <template slot-scope="s"><span class="mono">{{ s.row.candidateId }}</span></template>
          </el-table-column>
          <el-table-column label="Stability" width="220" align="center">
            <template slot-scope="s">
              <el-tag v-if="s.row.needsHumanReview" type="warning">
                samples disagreed by {{ s.row.dimensionSpread }}
              </el-tag>
              <el-tag v-else type="info">{{ s.row.samples }} samples agreed</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Reviewed by" width="160" align="center">
            <template slot-scope="s">
              <span v-if="s.row.reviewedBy">{{ s.row.reviewedBy }}</span>
              <el-tag v-else type="danger">not yet</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Graded" width="180" align="center">
            <template slot-scope="s">{{ s.row.createdTime }}</template>
          </el-table-column>
          <el-table-column label="" align="center">
            <template slot-scope="s">
              <el-button size="mini" type="primary" @click="open(s.row)">Review</el-button>
            </template>
          </el-table-column>
        </el-table>
        <!-- No score column anywhere above. A queue sorted by machine score has made the
             decision before anyone opens a row. -->
      </el-tab-pane>
    </el-tabs>

    <el-dialog :visible.sync="dialog" width="70%" :title="'Session ' + current.sessionId" top="4vh">
      <div v-if="stage === 'read'">
        <h3>1. Read the interview</h3>
        <p class="hint">The model's assessment is not shown yet, on purpose.</p>
        <div class="transcript">
          <div v-for="(t, i) in transcript" :key="i" :class="t.kind === 'ANSWER' ? 'answer' : 'question'">
            <strong>{{ t.kind === 'ANSWER' ? 'Candidate' : 'Interviewer' }}:</strong>
            <span>{{ t.text }}</span>
            <el-tag v-if="t.kind === 'ANSWER' && t.sttConfidence && t.sttConfidence < 0.85"
                    size="mini" type="warning" style="margin-left: 8px;">
              transcription uncertain — judge the content, not the wording
            </el-tag>
          </div>
        </div>
        <el-button type="primary" @click="stage = 'score'">I have read it — score it</el-button>
      </div>

      <div v-if="stage === 'score'">
        <h3>2. Your assessment</h3>
        <p class="hint">Your scores, before you see the model's. 1 poor, 3 meets the bar, 5 excellent.</p>
        <el-form label-width="190px">
          <el-form-item v-for="f in fields" :key="f.key" :label="f.label">
            <el-rate v-model="mine[f.key]" :max="5" show-score></el-rate>
          </el-form-item>
          <el-form-item label="Your decision">
            <el-radio-group v-model="mine.decision">
              <el-radio label="advance">Advance</el-radio>
              <el-radio label="needs_another_round">Another round</el-radio>
              <el-radio label="reject">Do not advance</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Your name">
            <el-input v-model="mine.reviewedBy" style="width: 240px;"></el-input>
          </el-form-item>
          <el-form-item label="Notes">
            <el-input type="textarea" v-model="mine.notes" :rows="3"></el-input>
          </el-form-item>
        </el-form>
        <el-button type="primary" :disabled="!canSubmit" @click="submit">
          Record my assessment and show the model's
        </el-button>
      </div>

      <div v-if="stage === 'compare'">
        <h3>3. What the model said</h3>
        <el-alert v-if="verdict.needsHumanReview" type="warning" :closable="false"
                  :title="verdict.reviewReason" style="margin-bottom: 12px;"></el-alert>
        <el-table :data="comparison" border size="mini" style="margin-bottom: 14px;">
          <el-table-column prop="dimension" label="Dimension" width="200"></el-table-column>
          <el-table-column prop="you" label="You" width="90" align="center"></el-table-column>
          <el-table-column prop="model" label="Model" width="90" align="center"></el-table-column>
          <el-table-column label="" align="center">
            <template slot-scope="s">
              <el-tag v-if="s.row.you && s.row.model && s.row.you !== s.row.model" type="warning" size="mini">
                you differ by {{ Math.abs(s.row.you - s.row.model) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4>Claims the model extracted</h4>
        <div class="claims">
          <div v-for="(c, i) in verdict.claims" :key="i">
            <el-tag size="mini" :type="c.status === 'correct' ? 'success' : (c.status === 'incorrect' ? 'danger' : 'info')">
              {{ c.status }}
            </el-tag>
            <span style="margin-left: 8px;">{{ c.claim }}</span>
          </div>
        </div>

        <h4>Evidence behind each score</h4>
        <div class="claims">
          <div v-for="(d, i) in verdict.dimensions" :key="i" style="margin-bottom: 8px;">
            <strong>{{ d.name }} — {{ d.score }}</strong>
            <div class="hint">{{ d.evidence }}</div>
            <div class="hint">{{ d.reasoning }}</div>
          </div>
        </div>

        <p class="hint" style="margin-top: 12px;">
          Model {{ verdict.model }} · {{ verdict.samples }} samples · rubric
          {{ (verdict.rubricHash || '').substring(0, 12) }} · schema
          {{ (verdict.schemaVersion || '').substring(0, 12) }}
        </p>
        <p class="hint">
          Your assessment is already recorded. Seeing this does not change it — that is deliberate.
        </p>
        <el-button @click="dialog = false; load()">Done</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
// The review console.
//
// WHY THE THREE STAGES ARE IN THIS ORDER
//
//   Read the transcript -> record your own scores -> then see the model's.
//
//   A console that shows the verdict and offers "approve" produces rubber-stamping. The documented
//   failure of decision-support tools is not that people ignore them, it is that they agree with
//   them - especially when agreeing is one click and disagreeing means justifying yourself. This
//   grader agreed with two human labelers at QWK 0.56 while they agreed with each other at 0.97,
//   and it docks a point of technical correctness for non-native phrasing at every competence
//   tier, where neither human docked any. An endorsement workflow would launder exactly that.
//
//   The reviewer's scores are submitted BEFORE the model's are fetched. Stage 3 is a comparison,
//   not an approval, and nothing on it can change what was already recorded.
//
//   It also produces the data the project most needs: every review is another human label on a
//   real interview, which is the only path from a twelve-transcript pilot to a ceiling measured on
//   production data.
module.exports = {
  data() {
    return {
      tab: "queue",
      unreviewedOnly: true,
      rows: [],
      dialog: false,
      stage: "read",
      current: { sessionId: "" },
      transcript: [],
      verdict: {},
      fields: [
        { key: "overall", label: "Overall" },
        { key: "correctness", label: "Correctness" },
        { key: "depth", label: "Depth" },
        { key: "communication", label: "Communication" },
        { key: "practicalExperience", label: "Practical experience" },
      ],
      mine: this.blank(),
    };
  },
  computed: {
    canSubmit() {
      return this.mine.overall > 0 && this.mine.decision && this.mine.reviewedBy;
    },
    comparison() {
      var dims = {};
      (this.verdict.dimensions || []).forEach(function (d) { dims[d.name] = d.score; });
      var self = this;
      return [
        { dimension: "Overall", you: self.mine.overall, model: self.verdict.overall },
        { dimension: "Correctness", you: self.mine.correctness, model: dims.correctness },
        { dimension: "Depth", you: self.mine.depth, model: dims.depth },
        { dimension: "Communication", you: self.mine.communication, model: dims.communication },
        { dimension: "Practical experience", you: self.mine.practicalExperience,
          model: dims.practical_experience },
      ];
    },
  },
  created() { this.load(); },
  methods: {
    blank() {
      return { overall: 0, correctness: 0, depth: 0, communication: 0,
               practicalExperience: 0, decision: "", reviewedBy: "", notes: "" };
    },
    load() {
      var self = this;
      this.$http.get("/review/queue?unreviewedOnly=" + this.unreviewedOnly).then(function (res) {
        if (res.data.status === 200) { self.rows = res.data.data || []; }
      });
    },
    open(row) {
      var self = this;
      this.current = row;
      this.mine = this.blank();
      this.verdict = {};
      this.stage = "read";
      this.$http.get("/review/" + row.sessionId + "/transcript").then(function (res) {
        if (res.data.status === 200) {
          self.transcript = res.data.data || [];
          self.dialog = true;
        }
      });
    },
    submit() {
      var self = this;
      var body = Object.assign({ sessionId: this.current.sessionId }, this.mine);
      // Recorded first. The verdict is only fetched afterwards, so it cannot influence what was
      // just submitted - which is the whole reason these are two calls.
      this.$http.post("/review/decide", body).then(function (res) {
        if (res.data.status !== 200) {
          self.$message.error("Could not record the assessment");
          return;
        }
        self.$http.get("/review/" + self.current.sessionId + "/verdict").then(function (r2) {
          if (r2.data.status === 200) {
            self.verdict = r2.data.data || {};
            self.stage = "compare";
          }
        });
      });
    },
  },
};
</script>

<style scoped>
.review-box { padding: 4px; }
.search-area { margin-bottom: 12px; }
.mono { font-family: Menlo, Consolas, monospace; font-size: 12px; }
.hint { color: #909399; font-size: 12px; line-height: 1.6; }
.transcript { max-height: 380px; overflow-y: auto; border: 1px solid #ebeef5;
              padding: 12px; margin-bottom: 14px; border-radius: 4px; }
.transcript .question { color: #606266; margin-bottom: 6px; }
.transcript .answer { color: #303133; margin-bottom: 14px; }
.claims { border: 1px solid #ebeef5; padding: 10px; border-radius: 4px; font-size: 13px; }
</style>

package com.majiang.counter.di

import android.content.Context
import androidx.room.Room
import com.majiang.counter.analysis.PolicyStudentInterpreter
import com.majiang.counter.analysis.SituationAnalyzer
import com.majiang.counter.analysis.TflitePolicyStudentInterpreter
import com.majiang.counter.data.AppDatabase
import com.majiang.counter.data.UserDao
import com.majiang.counter.ml.readTileDetectorAssetSpec
import com.majiang.counter.profile.AppProfile
import com.majiang.counter.rules.RulesConfig
import com.majiang.counter.rules.SichuanRulesEngine
import com.majiang.counter.vision.GatedTableTracker
import com.majiang.counter.vision.HandBottomRecognizer
import com.majiang.counter.vision.HudRemainingOcr
import com.majiang.counter.vision.MlKitHudRemainingOcr
import com.majiang.counter.vision.RiverDiffTableTracker
import com.majiang.counter.vision.TableTracker
import com.majiang.counter.vision.TfliteTileClassifier
import com.majiang.counter.vision.TileClassifier
import com.majiang.counter.vision.yolo.TfliteYoloTileDetector
import com.majiang.counter.vision.yolo.TileDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "majiang_counter.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun userDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun appProfile(): AppProfile = AppProfile.xuezhanDefault()

    @Provides
    @Singleton
    fun rulesConfig(): RulesConfig = RulesConfig()

    @Provides
    @Singleton
    fun sichuanRulesEngine(config: RulesConfig): SichuanRulesEngine =
        SichuanRulesEngine(config)

    @Provides
    @Singleton
    fun policyStudentInterpreter(impl: TflitePolicyStudentInterpreter): PolicyStudentInterpreter = impl

    @Provides
    @Singleton
    fun situationAnalyzer(
        engine: SichuanRulesEngine,
        policyStudent: PolicyStudentInterpreter,
    ): SituationAnalyzer = SituationAnalyzer(engine, policyStudent)

    @Provides
    @Singleton
    fun tileClassifier(impl: TfliteTileClassifier): TileClassifier = impl

    @Provides
    @Singleton
    fun tileDetector(
        @ApplicationContext context: Context,
        appProfile: AppProfile,
    ): TileDetector {
        val spec = readTileDetectorAssetSpec(context, appProfile.appId)
        return TfliteYoloTileDetector(context, spec.assetPath, spec.inputWidth, spec.inputHeight)
    }

    @Provides
    @Singleton
    fun hudRemainingOcr(impl: MlKitHudRemainingOcr): HudRemainingOcr = impl

    @Provides
    @Singleton
    fun riverDiffTableTracker(
        classifier: TileClassifier,
        tileDetector: TileDetector,
    ): RiverDiffTableTracker = RiverDiffTableTracker(classifier, tileDetector)

    @Provides
    @Singleton
    fun gatedTableTracker(inner: RiverDiffTableTracker): GatedTableTracker = GatedTableTracker(inner)

    @Provides
    @Singleton
    fun tableTracker(gated: GatedTableTracker): TableTracker = gated

    @Provides
    @Singleton
    fun handBottomRecognizer(classifier: TileClassifier): HandBottomRecognizer =
        HandBottomRecognizer(classifier)
}

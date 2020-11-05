package net.fabricmc.example.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.structures.JigsawJunction;
import net.minecraft.world.level.levelgen.feature.structures.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseChunkGenerator extends ChunkGenerator {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	@Shadow @Final private int chunkCountX;

	@Shadow @Final private int chunkCountY;

	@Shadow @Final private int chunkCountZ;

	@Shadow @Final private int chunkHeight;

	@Shadow @Final private int chunkWidth;

	public MixinNoiseChunkGenerator(BiomeSource biomeSource, StructureSettings structureSettings) {
		super(biomeSource, structureSettings);
	}

	@Shadow
	private static double getContribution(int i, int j, int k) {
		return 0;
	}

	@Shadow @Final private @Nullable SimplexNoise islandNoise;

	@Shadow @Final protected Supplier<NoiseGeneratorSettings> settings;

	@Shadow public abstract int getSeaLevel();

	@Shadow @Final private static float[] BIOME_WEIGHTS;

	@Shadow protected abstract double getRandomDensity(int i, int j);

	@Shadow protected abstract double sampleAndClampNoise(int i, int j, int k, double d, double e, double f, double g);

	@Shadow @Final private int height;

	@Shadow @Final protected BlockState defaultFluid;

	@Shadow @Final protected BlockState defaultBlock;

	private void fillNoiseColumn(double[] ds, int i, int j) {
		NoiseSettings noiseSettings = ((NoiseGeneratorSettings)this.settings.get()).noiseSettings();
		double ac;
		double ad;
		double ai;
		double aj;
		if (this.islandNoise != null) {
			ac = (double)(TheEndBiomeSource.getHeightValue(this.islandNoise, i, j) - 8.0F);
			if (ac > 0.0D) {
				ad = 0.25D;
			} else {
				ad = 1.0D;
			}
		} else {
			float g = 0.0F;
			float h = 0.0F;
			float k = 0.0F;
			int m = this.getSeaLevel();
			float n = this.biomeSource.getNoiseBiome(i, m, j).getDepth();

			for(int o = -2; o <= 2; ++o) {
				for(int p = -2; p <= 2; ++p) {
					Biome biome = this.biomeSource.getNoiseBiome(i + o, m, j + p);
					// custom: depth + 5.4
					float q = biome.getDepth() + 5.4f;
					// TODO: scale is technically wrong. I love how it looks but it's still wrong :P
					float r = biome.getScale();
					float u;
					float v;
					if (noiseSettings.isAmplified() && q > 0.0F) {
						u = 1.0F + q * 2.0F;
						v = 1.0F + r * 4.0F;
					} else {
						u = q;
						v = r;
					}

					float w = q > n ? 0.5F : 1.0F;
					float x = w * BIOME_WEIGHTS[o + 2 + (p + 2) * 5] / (u + 2.0F);
					g += v * x;
					h += u * x;
					k += x;
				}
			}

			float y = h / k;
			float z = g / k;
			ai = (double)(y * 0.5F - 0.125F);
			aj = (double)(z * 0.9F + 0.1F);
			ac = ai * 0.265625D;
			ad = 96.0D / aj;
		}

		double ae = 684.412D * noiseSettings.noiseSamplingSettings().xzScale();
		double af = 684.412D * noiseSettings.noiseSamplingSettings().yScale();
		double ag = ae / noiseSettings.noiseSamplingSettings().xzFactor();
		double ah = af / noiseSettings.noiseSamplingSettings().yFactor();
		double ao = noiseSettings.randomDensityOffset() ? this.getRandomDensity(i, j) : 0.0D;
		double ap = noiseSettings.densityFactor();
		double aq = noiseSettings.densityOffset();

		// custom: y pieces * 2
		for(int ar = 0; ar <= this.chunkCountY * 2; ++ar) {
			double noise = this.sampleAndClampNoise(i, ar, j, ae, af, ag, ah);
			// custom: y pieces * 2
			double at = 1.0D - (double)ar * 2.0D / ((double)this.chunkCountY * 2) + ao;
			double au = at * ap + aq;
			double av = (au + ac) * ad;

			// custom: 0..256 has normal terrain, -256..0 has new terrain
			if (ar > 32) {
				if (av > 0.0D) {
					noise += av * 4.0D;
				} else {
					noise += av;
				}
			} else {
				if (ar < 8) {
					noise += (8 - ar) * 24;
				}

				if (ar > 24) {
					noise += (ar - 24) * 24;
				}
			}

			ds[ar] = noise;
		}

	}

	/**
	 * @author SuperCoder79
	 */
	@Overwrite
	public void fillFromNoise(LevelAccessor levelAccessor, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess) {
		ObjectList<StructurePiece> objectList = new ObjectArrayList(10);
		ObjectList<JigsawJunction> objectList2 = new ObjectArrayList(32);
		ChunkPos chunkPos = chunkAccess.getPos();
		int i = chunkPos.x;
		int j = chunkPos.z;
		int k = SectionPos.sectionToBlockCoord(i);
		int l = SectionPos.sectionToBlockCoord(j);
		Iterator var11 = StructureFeature.NOISE_AFFECTING_FEATURES.iterator();

		while(var11.hasNext()) {
			StructureFeature<?> structureFeature = (StructureFeature)var11.next();
			structureFeatureManager.startsForFeature(SectionPos.of(chunkPos, 0), structureFeature).forEach((structureStart) -> {
				Iterator var6 = structureStart.getPieces().iterator();

				while(true) {
					while(true) {
						StructurePiece structurePiece;
						do {
							if (!var6.hasNext()) {
								return;
							}

							structurePiece = (StructurePiece)var6.next();
						} while(!structurePiece.isCloseToChunk(chunkPos, 12));

						if (structurePiece instanceof PoolElementStructurePiece) {
							PoolElementStructurePiece poolElementStructurePiece = (PoolElementStructurePiece)structurePiece;
							StructureTemplatePool.Projection projection = poolElementStructurePiece.getElement().getProjection();
							if (projection == StructureTemplatePool.Projection.RIGID) {
								objectList.add(poolElementStructurePiece);
							}

							Iterator var10 = poolElementStructurePiece.getJunctions().iterator();

							while(var10.hasNext()) {
								JigsawJunction jigsawJunction = (JigsawJunction)var10.next();
								int kx = jigsawJunction.getSourceX();
								int lx = jigsawJunction.getSourceZ();
								if (kx > k - 12 && lx > l - 12 && kx < k + 15 + 12 && lx < l + 15 + 12) {
									objectList2.add(jigsawJunction);
								}
							}
						} else {
							objectList.add(structurePiece);
						}
					}
				}
			});
		}

		double[][][] ds = new double[2][this.chunkCountZ + 1][this.chunkCountY * 2+ 1];

		// custom: y pieces * 2
		for(int m = 0; m < this.chunkCountZ + 1; ++m) {
			ds[0][m] = new double[this.chunkCountY * 2 + 1];
			this.fillNoiseColumn(ds[0][m], i * this.chunkCountX, j * this.chunkCountZ + m);
			ds[1][m] = new double[this.chunkCountY * 2 + 1];
		}

		ProtoChunk protoChunk = (ProtoChunk)chunkAccess;
		Heightmap heightmap = protoChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap heightmap2 = protoChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		ObjectListIterator<StructurePiece> objectListIterator = objectList.iterator();
		ObjectListIterator<JigsawJunction> objectListIterator2 = objectList2.iterator();

		for(int n = 0; n < this.chunkCountX; ++n) {
			int p;
			for(p = 0; p < this.chunkCountZ + 1; ++p) {
				this.fillNoiseColumn(ds[1][p], i * this.chunkCountX + n + 1, j * this.chunkCountZ + p);
			}

			for(p = 0; p < this.chunkCountZ; ++p) {
				LevelChunkSection levelChunkSection = protoChunk.getOrCreateSection(protoChunk.getSectionsCount() - 1);
				levelChunkSection.acquire();

				// custom: y pieces * 2
				for(int q = this.chunkCountY * 2 - 1; q >= 0; --q) {
					double d = ds[0][p][q];
					double e = ds[0][p + 1][q];
					double f = ds[1][p][q];
					double g = ds[1][p + 1][q];
					double h = ds[0][p][q + 1];
					double r = ds[0][p + 1][q + 1];
					double s = ds[1][p][q + 1];
					double t = ds[1][p + 1][q + 1];

					for(int u = this.chunkHeight - 1; u >= 0; --u) {
						// custom: push down y by 256
						int v = q * this.chunkHeight + u - 256;
						int w = v & 15;
						int x = protoChunk.getSectionIndex(v);
						if (protoChunk.getSectionIndex(levelChunkSection.bottomBlockY()) != x) {
							levelChunkSection.release();
							levelChunkSection = protoChunk.getOrCreateSection(x);
							levelChunkSection.acquire();
						}

						double y = (double)u / (double)this.chunkHeight;
						double z = Mth.lerp(y, d, h);
						double aa = Mth.lerp(y, f, s);
						double ab = Mth.lerp(y, e, r);
						double ac = Mth.lerp(y, g, t);

						for(int ad = 0; ad < this.chunkWidth; ++ad) {
							int ae = k + n * this.chunkWidth + ad;
							int af = ae & 15;
							double ag = (double)ad / (double)this.chunkWidth;
							double ah = Mth.lerp(ag, z, aa);
							double ai = Mth.lerp(ag, ab, ac);

							for(int aj = 0; aj < this.chunkWidth; ++aj) {
								int ak = l + p * this.chunkWidth + aj;
								int al = ak & 15;
								double am = (double)aj / (double)this.chunkWidth;
								double an = Mth.lerp(am, ah, ai);
								double ao = Mth.clamp(an / 200.0D, -1.0D, 1.0D);

								int at;
								int au;
								int ar;
								for(ao = ao / 2.0D - ao * ao * ao / 24.0D; objectListIterator.hasNext(); ao += getContribution(at, au, ar) * 0.8D) {
									StructurePiece structurePiece = (StructurePiece)objectListIterator.next();
									BoundingBox boundingBox = structurePiece.getBoundingBox();
									at = Math.max(0, Math.max(boundingBox.x0 - ae, ae - boundingBox.x1));
									au = v - (boundingBox.y0 + (structurePiece instanceof PoolElementStructurePiece ? ((PoolElementStructurePiece)structurePiece).getGroundLevelDelta() : 0));
									ar = Math.max(0, Math.max(boundingBox.z0 - ak, ak - boundingBox.z1));
								}

								objectListIterator.back(objectList.size());

								while(objectListIterator2.hasNext()) {
									JigsawJunction jigsawJunction = (JigsawJunction)objectListIterator2.next();
									int as = ae - jigsawJunction.getSourceX();
									at = v - jigsawJunction.getSourceGroundY();
									au = ak - jigsawJunction.getSourceZ();
									ao += getContribution(as, at, au) * 0.4D;
								}

								objectListIterator2.back(objectList2.size());
								BlockState blockState = this.generateBaseState(ao, v);
								if (blockState != AIR) {
									if (blockState.getLightEmission() != 0) {
										mutableBlockPos.set(ae, v, ak);
										protoChunk.addLight(mutableBlockPos);
									}

									levelChunkSection.setBlockState(af, w, al, blockState, false);
									heightmap.update(af, v, al, blockState);
									heightmap2.update(af, v, al, blockState);
								}
							}
						}
					}
				}

				levelChunkSection.release();
			}

			double[][] es = ds[0];
			ds[0] = ds[1];
			ds[1] = es;
		}

	}

	/**
	 * @author SuperCoder79
	 */
	@Overwrite
	public BlockGetter getBaseColumn(int i, int j) {
		// custom: y pieces * 2
		BlockState[] blockStates = new BlockState[this.chunkCountY * 2 * this.chunkHeight];
		this.iterateNoiseColumn(i, j, blockStates, (Predicate)null);
		return new NoiseColumn(blockStates);
	}

	/**
	 * @author SuperCoder79
	 */
	@Overwrite
	private int iterateNoiseColumn(int i, int j, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate) {
		int k = Math.floorDiv(i, this.chunkWidth);
		int l = Math.floorDiv(j, this.chunkWidth);
		int m = Math.floorMod(i, this.chunkWidth);
		int n = Math.floorMod(j, this.chunkWidth);
		double d = (double)m / (double)this.chunkWidth;
		double e = (double)n / (double)this.chunkWidth;
		double[][] ds = new double[][]{this.makeAndFillNoiseColumn(k, l), this.makeAndFillNoiseColumn(k, l + 1), this.makeAndFillNoiseColumn(k + 1, l), this.makeAndFillNoiseColumn(k + 1, l + 1)};

		// custom: y pieces * 2
		for(int o = this.chunkCountY * 2 - 1; o >= 0; --o) {
			double f = ds[0][o];
			double g = ds[1][o];
			double h = ds[2][o];
			double p = ds[3][o];
			double q = ds[0][o + 1];
			double r = ds[1][o + 1];
			double s = ds[2][o + 1];
			double t = ds[3][o + 1];

			for(int u = this.chunkHeight - 1; u >= 0; --u) {
				double v = (double)u / (double)this.chunkHeight;
				double w = Mth.lerp3(v, d, e, f, q, h, s, g, r, p, t);
				int x = o * this.chunkHeight + u;
				BlockState blockState = this.generateBaseState(w, x);
				if (blockStates != null) {
					blockStates[x] = blockState;
				}

				if (predicate != null && predicate.test(blockState)) {
					return x + 1;
				}
			}
		}

		return 0;
	}

	/**
	 * @author
	 */
	@Overwrite
	public BlockState generateBaseState(double d, int i) {
		BlockState blockState3;
		if (d > 0.0D) {
			blockState3 = this.defaultBlock;
			// custom: no water under 0
		} else if (i < this.getSeaLevel() && i >= 0) {
			blockState3 = this.defaultFluid;
		} else {
			blockState3 = AIR;
		}

		return blockState3;
	}

	/**
	 * @author
	 */
	@Overwrite
	private double[] makeAndFillNoiseColumn(int i, int j) {
		// custom: y pieces * 2
		double[] ds = new double[this.chunkCountY * 2 + 1];
		this.fillNoiseColumn(ds, i, j);
		return ds;
	}

	/**
	 * @author
	 */
	@Overwrite
	private void setBedrock(ChunkAccess chunkAccess, Random random) {
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		int i = chunkAccess.getPos().getMinBlockX();
		int j = chunkAccess.getPos().getMinBlockZ();
		NoiseGeneratorSettings noiseGeneratorSettings = (NoiseGeneratorSettings)this.settings.get();
		int k = noiseGeneratorSettings.getBedrockFloorPosition();
		int l = this.height - 1 - noiseGeneratorSettings.getBedrockRoofPosition();
		boolean bl = l + 5 - 1 >= 0 && l < this.height;
		boolean bl2 = k + 5 - 1 >= 0 && k < this.height;
		if (bl || bl2) {
			Iterator var12 = BlockPos.betweenClosed(i, 0, j, i + 15, 0, j + 15).iterator();

			while(true) {
				BlockPos blockPos;
				int o;
				do {
					if (!var12.hasNext()) {
						return;
					}

					blockPos = (BlockPos)var12.next();
					if (bl) {
						for(o = 0; o < 5; ++o) {
							if (o <= random.nextInt(5)) {
								chunkAccess.setBlockState(mutableBlockPos.set(blockPos.getX(), l - o, blockPos.getZ()), Blocks.BEDROCK.defaultBlockState(), false);
							}
						}
					}
				} while(!bl2);

				for(o = 4; o >= 0; --o) {
					if (o <= random.nextInt(5)) {
						// custom: push down y by 256
						chunkAccess.setBlockState(mutableBlockPos.set(blockPos.getX(), -256 + o, blockPos.getZ()), Blocks.BEDROCK.defaultBlockState(), false);
					}
				}
			}
		}
	}
}

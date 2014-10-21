package org.seagatesoft.sde.dataregionsfinder;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.seagatesoft.sde.DataRegion;
import org.seagatesoft.sde.MultiKeyMap;
import org.seagatesoft.sde.TagNode;
import org.seagatesoft.sde.treematcher.TreeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiningDataRegions implements DataRegionsFinder
  {
  private static Logger logger = LoggerFactory.getLogger( MiningDataRegions.class );

  private TreeMatcher treeMatcher;

  public MiningDataRegions( TreeMatcher treeMatcher )
    {
    this.treeMatcher = treeMatcher;
    }

  public TreeMatcher getTreeMatcher()
    {
    return treeMatcher;
    }

  public List<DataRegion> findDataRegions( TagNode tagNode, int childNumber, int maxNodeInGeneralizedNodes, double similarityTreshold )
    {
    // List untuk menyimpan seluruh data region yang ditemukan
    List<DataRegion> dataRegions = new ArrayList<DataRegion>();
    // List untuk menyimpan kandidat data region, sepertinya tidak perlu
    List<DataRegion> currentDataRegions = new ArrayList<DataRegion>();

    if( tagNode.subTreeDepth() >= 2 )
      {
      // lakukan pembandingan2 antara generalized nodes yang mungkin dari children milik tagNode
      Map<ComparisonResultKey, Double> comparisonResults = compareGeneralizedNodes( tagNode, maxNodeInGeneralizedNodes );
      // identifikasi data region dari hasil pembandingan generalized nodes
      currentDataRegions = identifyDataRegions( 1, tagNode, maxNodeInGeneralizedNodes, similarityTreshold, comparisonResults );

      logger.info( String.format( "comparisonResults: count=%d; currentDataRegions: level=%d, child=%d, count=%d", comparisonResults.size(), tagNode.getLevel(), childNumber, currentDataRegions.size() ) );

      // tambahkan currentDataRegions yang ditemukan jika ada pada dataRegions
      if( !currentDataRegions.isEmpty() )
        {
        dataRegions.addAll( currentDataRegions );
        }

      // buat array yang menyatakan child mana saja dari tagNode yang termasuk dalam data region
      boolean[] childCoveredArray = new boolean[ tagNode.childrenCount() ];

      for( DataRegion dataRegion : currentDataRegions )
        {
        for( int childCounter = dataRegion.getStartPoint(); childCounter < dataRegion.getStartPoint() + dataRegion.getNodesCovered(); childCounter++ )
          {
          childCoveredArray[ childCounter - 1 ] = true;
          }
        }

      // cari data regions dari children yang tidak termasuk dalam currentDataRegions, rekursif
      for( int childCounter = 0; childCounter < childCoveredArray.length; childCounter++ )
        {
        if( !childCoveredArray[ childCounter ] )
          {
          // logger.info( String.format("findDataRegions level=%d, child=%d", tagNode.getLevel(), childCounter));
          dataRegions.addAll( findDataRegions( tagNode.getChildAtNumber( childCounter + 1 ), childCounter, maxNodeInGeneralizedNodes, similarityTreshold ) );
          }
        }
      }

    return dataRegions;
    }

  private Map<ComparisonResultKey, Double> compareGeneralizedNodes( TagNode tagNode, int maxNodeInGeneralizedNodes )
    {
    Map<ComparisonResultKey, Double> comparisonResults = new MultiKeyMap<ComparisonResultKey, Double>();

    // mulai dari setiap node (start of each node)
    for( int childCounter = 1; childCounter <= maxNodeInGeneralizedNodes; childCounter++ )
      {
      // membandingkan kombinasi yang berbeda mulai dari childCounter sampai maxNodeInGeneralizedNodes
      // (comparing different combinations ranging from childCounter to maxNodeInGeneralizedNodes)
      for( int combinationSize = childCounter; combinationSize <= maxNodeInGeneralizedNodes; combinationSize++ )
        {
        // minimal terdapat sepasang generalized nodes, sepertinya redundan dengan pengecekan di bawah
        // (There are at least a pair of generalized nodes , it seems redundant to check under)
        // TO BE OPTIMIZED
        if( tagNode.getChildAtNumber( childCounter + 2 * combinationSize - 1 ) != null )
          {
          int startPoint = childCounter;

          // mulai melakukan pembandingan pasangan-pasangan generalized nodes
          // (began to make comparisons generalized pairs of nodes)
          // BEDA DENGAN JURNAL, kondisi <=, untuk mengatasi kasus ketika childCounter = 1, combinationSize = 1, dan startPoint = tagNode.childrenCount() - 1
          // WITH DIFFERENT JOURNAL , condition < = , to address the case when childCounter = 1 , combinationSize = 1 , and startpoint = tagNode.childrenCount ( ) - 1

          for( int nextPoint = childCounter + combinationSize; nextPoint <= tagNode.childrenCount(); nextPoint = nextPoint + combinationSize )
            {
            // lakukan pembandingan jika terdapat generalized nodes selanjutnya dengan ukuran yang sama
            // (do comparisons if there are further generalized nodes with the same size)
            if( tagNode.getChildAtNumber( nextPoint + combinationSize - 1 ) != null )
              {
              // buat array dari kedua generalized nodes yang akan dibandingkan
              TagNode[] A = new TagNode[ combinationSize ];
              TagNode[] B = new TagNode[ combinationSize ];

              // isi array daftar nomor children dari tagNode yang termasuk dalam generalized node A
              // the contents of the array list of tagNode the number of children included in the generalized node A
              int arrayCounter = 0;
              for( int i = startPoint; i < nextPoint; i++ )
                {
                A[ arrayCounter ] = tagNode.getChildAtNumber( i );
                arrayCounter++;
                }

              // isi array daftar nomor children dari tagNode yang termasuk dalam generalized node A
              arrayCounter = 0;
              for( int i = nextPoint; i < ( nextPoint + combinationSize ); i++ )
                {
                B[ arrayCounter ] = tagNode.getChildAtNumber( i );
                arrayCounter++;
                }

              // simpan hasil pembandingan (save the results of benchmarking)
              ComparisonResultKey key = new ComparisonResultKey( tagNode, combinationSize, startPoint );
              comparisonResults.put( key, treeMatcher.normalizedMatchScore( A, B ) );
              startPoint = nextPoint;
              }
            }
          }
        }
      }

    return comparisonResults;
    }

  private List<DataRegion> identifyDataRegions( int initStartPoint, TagNode tagNode, int maxNodeInGeneralizedNodes, double similarityTreshold, Map<ComparisonResultKey, Double> comparisonResults )
    {
    List<DataRegion> dataRegions = new ArrayList<DataRegion>();
    DataRegion maxDR = new DataRegion( tagNode, 0, 0, 0 );
    DataRegion currentDR = new DataRegion( tagNode, 0, 0, 0 );

    // mulai dari tiap kombinasi (start of each combination)
    for( int combinationSize = 1; combinationSize <= maxNodeInGeneralizedNodes; combinationSize++ )
      {
      // mulai dari tiap startPoint (start of each startpoint)
      // BEDA dengan jurnal, <, untuk efisiensi karena perbandingan ke-initStartPoint+combinationSize tidak perlu
      // (DIFFERENT by the journal , < , for efficiency due to the comparison - initStartPoint + combinationSize not need)
      for( int startPoint = initStartPoint; startPoint < initStartPoint + combinationSize; startPoint++ )
        {
        boolean flag = true;
        // BEDA dengan jurnal, childNumber+2*combinationSize-1 <=, karena belum tentu setiap ComparisonResultKey(tagNode, combinationSize, childNumber) ada
        // DIFFERENT by the journal , childNumber + 2 * combinationSize - 1 < = , because not every ComparisonResultKey ( tagNode , combinationSize , childNumber ) there
        for( int childNumber = startPoint; childNumber + 2 * combinationSize - 1 <= tagNode.childrenCount(); childNumber += combinationSize )
          {
          ComparisonResultKey key = new ComparisonResultKey( tagNode, combinationSize, childNumber );

          if( comparisonResults.get( key ) >= similarityTreshold )
            {
            // jika cocok untuk pertama kali (if it is suitable for first time)
            if( flag )
              {
              currentDR.setCombinationSize( combinationSize );
              currentDR.setStartPoint( childNumber );
              currentDR.setNodesCovered( 2 * combinationSize );
              flag = false;
              }
            // jika cocok bukan untuk pertama kali (if it is not suitable for first time)
            else
              {
              currentDR.setNodesCovered( currentDR.getNodesCovered() + combinationSize );
              }
            }
          // jika tidak cocok dan sebelumnya cocok
          else if( !flag )
            {
            break;
            }
          }

        // jika currentDR yang baru ditemukan mencakup lebih banyak nodes dan dimulai dari posisi yang lebih awal atau sama dengan maxDR
        // (if the newly discovered currentDR include more nodes and starting from that position earlier or equal to maxDR)
        if( ( maxDR.getNodesCovered() < currentDR.getNodesCovered() ) && ( maxDR.getStartPoint() == 0 || currentDR.getStartPoint() <= maxDR.getStartPoint() ) )
          {
          maxDR.setCombinationSize( currentDR.getCombinationSize() );
          maxDR.setStartPoint( currentDR.getStartPoint() );
          maxDR.setNodesCovered( currentDR.getNodesCovered() );
          }
        }
      }

    // jika ditemukan data region (if found the data region)
    if( maxDR.getNodesCovered() != 0 )
      {
      dataRegions.add( maxDR );

      // jika data region yang ditemukan masih menyisakan children yang belum dicari,
      // (if the data still leaves the region found children who have not sought)
      // maka cari data region mulai dari child setelah child terakhir dari data region
      // (then locate the data region ranging from the child after the last child of the data region)
      if( maxDR.getStartPoint() + maxDR.getNodesCovered() - 1 != tagNode.childrenCount() )
        {
        dataRegions.addAll( identifyDataRegions( maxDR.getStartPoint() + maxDR.getNodesCovered(), tagNode, maxNodeInGeneralizedNodes, similarityTreshold, comparisonResults ) );
        }
      }

    return dataRegions;
    }
  }
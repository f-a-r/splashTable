#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <math.h>
#include <stdint.h>
#include <emmintrin.h>
#include <smmintrin.h>
#include <stdbool.h>
#define MAXBUCKETS 4
#define MAXMULTIPLIERS 2
#define DEFAULT_BUFFER 1024
#define MAXNUMOFPROBE 1024
//**********************************************

FILE *fdmp;
int32_t B,S,h,N;
uint32_t numOfBuckets;
uint32_t multipliers[MAXMULTIPLIERS];
char linebuf[100]={0};


//Method to read next line from dumpfile
int getnextline(FILE * fin, char string[])
{
  char temp;
  int length;

  temp = fgetc(fin);
  length = 0;

  while((temp != '\n'))
    {
      if(feof(fin)){
	*(string+length) = 0;
	return(0);
      }
      *(string+length) = temp;
      length ++;
      temp = fgetc(fin);
    }

  *(string+length) = 0;
  return (1);
}

//Helper Method to read Search keys line by line from probefile I got this method from stackoverflow
char* getProbe(void)
{
  char line[100];  /* Generously large value for most situations */
  char *searchKey;
  line[0] = '\0';
  line[sizeof(line)-1] = ~'\0';  /* Ensure no false-null at end of buffer */
  searchKey = fgets(line, sizeof(line), stdin);
  return searchKey;
}

//Probe Method to probe each search key passed to it
uint32_t probe(char* searchKey, uint32_t SplashKeys[][B], uint32_t SplashPayloads[][B])
{
  uint32_t probeKey = atoi(searchKey);
  // copy the key in 4 copies of the key
  __m128i probeKeyCopies =  _mm_setr_epi32 (probeKey,probeKey,probeKey,probeKey);
  //Combine the two multipliers to use in multiplication with probekeycopies
  __m128i multipliers1 =  _mm_setr_epi32 (multipliers[0],0, multipliers[1], 0);
  //Set packed 64-bit integers in dst with the supplied values.
  __m128i numOfBucketsSet = _mm_setr_epi32 (numOfBuckets, 0, numOfBuckets,0);

  //key * multiplier: Multiply the packed 32-bit integers in a and b, producing intermediate 64-bit integers
  //and store the low 32 bits of the intermediate integers in dst. 
  __m128i keyMulByMulitpliers = _mm_mullo_epi32(multipliers1,probeKeyCopies);
  //get table slots by SIMD mul of number of bucket and keyMulByMultipliers
  __m128i BucketNumbers = _mm_mul_epu32 (keyMulByMulitpliers,numOfBucketsSet);

  uint32_t BucketNumber[4];
  _mm_store_si128(&BucketNumber,BucketNumbers);

  // Only indecis 1 and 3 of BucketNumber hold the two buckets which a key would map to using our hash multipliers
  // So I would extract those and save them in Bucket1 and Bucket2 . I will use this buckets to extract the keys which are stored
  // in SplashKeys and Payloads which are saved in SplashPayloads

  uint32_t Bucket1 = BucketNumber[1];
  uint32_t Bucket2 = BucketNumber[3];
  //We now compare the 4 copies of the search key saved in a __m128i with the keys in 
  //each of the two buckets
  //First to do the parallel compare, we have to save the keys in each bucket in a type __m128i as well as the associated payloads
  //using the instruction below:
  __m128i FirstBucket= _mm_load_si128 (SplashKeys[Bucket1]);
  __m128i SecondBucket= _mm_load_si128 (SplashKeys[Bucket2]);
  __m128i FirstBucketPayloads = _mm_load_si128 (SplashPayloads[Bucket1]);
  __m128i SecondBucketPayloads = _mm_load_si128 (SplashPayloads[Bucket2]);

  //Now that we have data i.e. the four copies of search key in an _m128i and the 4keys of the first and second target bucket
  //in _m128i we need to compare them
  __m128i compareFirstBucketVsKeys = _mm_cmpeq_epi32 (probeKeyCopies, FirstBucket);
  __m128i compareSecondBucketVsKeys = _mm_cmpeq_epi32 (probeKeyCopies, SecondBucket);

  //And compared result of keys vs payloads
  __m128i KeysAndWithPayloads1 = _mm_and_si128 (compareFirstBucketVsKeys, FirstBucketPayloads);
  __m128i KeysAndWithPayloads2 = _mm_and_si128 (compareSecondBucketVsKeys, SecondBucketPayloads);

  //Or the two above:
  __m128i SimdOr = _mm_or_si128 (KeysAndWithPayloads1, KeysAndWithPayloads2);

  //SIMD OR Across: There was no OR-ACROSS instruction to do it using in one instruction, or we couldn't find one!
  //So I will save every 32 bit of simdOr in 4 ,32 bit integers and do OR across them in 3 steps
  uint32_t Or_Across[4];
  _mm_store_si128(&Or_Across,SimdOr);

  __m128i Or_Across_set1 = _mm_set_epi32 (Or_Across[3], Or_Across[3], Or_Across[3], Or_Across[3]);
  __m128i Or_Across_set2 = _mm_set_epi32 (Or_Across[2], Or_Across[2], Or_Across[2], Or_Across[2]);
  __m128i First_Set_Of_Or = _mm_or_si128 (Or_Across_set1, Or_Across_set2);

  __m128i Or_Across_set3 = _mm_set_epi32 (Or_Across[1], Or_Across[1], Or_Across[1], Or_Across[1]);
  __m128i Second_Set_Of_Or = _mm_or_si128 (First_Set_Of_Or, Or_Across_set3);

  __m128i Or_Across_set4 = _mm_set_epi32 (Or_Across[0], Or_Across[0], Or_Across[0], Or_Across[0]);
  __m128i Third_Set_Of_Or = _mm_or_si128 (Second_Set_Of_Or, Or_Across_set4);


  uint32_t Payloads_Found[4];
  _mm_store_si128(&Payloads_Found,Third_Set_Of_Or);
  // any of 0 to 3 indecis has the same  value
  return Payloads_Found[1];

}


//*****************************************************************************************//
int main(int argc, char *argv[])
{
  size_t nbytes = 0;
  char* first = NULL;
  char* dumpfile = argv[1];
  char* probefile = argv[2];
  char temp1[20],temp2[20]; // variable for keeping the long data i.e. multipliers from dump when reading the second line
  uint32_t key = 0, value = 0;
  bool fill = true;
  char* probeKey1;
  uint32_t valueForProbe;

  if (!dumpfile)
    {
      printf("\n No dumpfile provided! ");
      abort();    
    }

  //Reading from dump:
  if ((fdmp = fopen(dumpfile, "r")) == NULL)
    {
      printf("\n Error: Unable to open dump file ");
      abort();
    }

  //Reading the first line and getting B,S,h,N and finding number of buckets in the splash table
  getnextline(fdmp, linebuf);
  if (sscanf(linebuf,"%d%d%d%d",&B,&S,&h,&N)==EOF)
    {
      printf("\n Error: Invalid data  ");
      return 1;
    }
  else
    {
      if (B != 4 || h != 2)
	{
	  fprintf(stderr, "Wrong value for B/h!\n");
	  abort();
	}

      //printf ("B = %d S = %d h = %d N = %d\n",B,S,h,N);
      numOfBuckets =  pow(2,S)/B;
      //printf("Number of buckets in splash table = %d\n", numOfBuckets);
    }

  uint32_t BucketKeys [numOfBuckets][B];  //Array for saving keys of each bucket
  uint32_t BucketPayloads [numOfBuckets][B];//Array for saving payloads of each bucket
  //Reading second line and getting multipliers:
  getnextline(fdmp, linebuf);
  if (sscanf(linebuf,"%s%s",temp1,temp2)==EOF)
    {
      printf("\n Error: Invalid data  ");
      return 1;
    }
  else
    {
      multipliers[0] = strtoull(temp1,NULL,10);
      multipliers[1] = strtoull(temp2,NULL,10);
      //printf("multipler 1 = %u multipler 2 = %u\n",multipliers[0],multipliers[1]);

    }
  //reconstruct the Splashtable to be used in the probe process
  while (fill)
    {
      for (int eachBucket = 0 ; eachBucket < numOfBuckets ; eachBucket++)
	{
	  for (int j = 0; j < B; j++)
	    {
	      if(getnextline(fdmp, linebuf))
		{
		  if (sscanf(linebuf,"%u%u",&key,&value)==EOF)
		    {
		      printf("\n Error: Invalid key value  ");
		      break;
		    }

		  else 
		    {
		      //Saving the key from index 0 to 3 and associated payloads from index 0 to 3 in BucketPayloads array
		      BucketKeys[eachBucket][j] = key;
		      BucketPayloads[eachBucket][j] = value;
		    }
		}
	    }
	}
      fill = false;
    }
  //Reading the search key one by one from the probefile and calling the probe method for each search key in the probefile
  //If the item is present in the splashtable then we will print the key+'space'+payload
  while ((probeKey1=getProbe()) != NULL)
    {
      uint64_t foundProbe = strtoull (probeKey1,NULL,10);
      if ((valueForProbe = probe(probeKey1,BucketKeys,BucketPayloads)) != 0 )
	{
	  printf("%lli ", foundProbe);
	  printf("%li\n", valueForProbe);
	}
    }
  //printf("\n");
  return 0;

}

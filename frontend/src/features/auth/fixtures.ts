// 셸 확인용 더미 — 데이터 연결 시 삭제

export interface TeamFixture { id: string; abbr: string; name: string; }
export interface PlayerFixture { id: number; name: string; team: string; }

export const teamFixtures: TeamFixture[] = [
  ['ari', 'ARI', 'Arizona'], ['atl', 'ATL', 'Atlanta'], ['bal', 'BAL', 'Baltimore'],
  ['bos', 'BOS', 'Boston'], ['chc', 'CHC', 'Chi. Cubs'], ['chw', 'CHW', 'Chi. White Sox'],
  ['cin', 'CIN', 'Cincinnati'], ['cle', 'CLE', 'Cleveland'], ['col', 'COL', 'Colorado'],
  ['det', 'DET', 'Detroit'], ['hou', 'HOU', 'Houston'], ['kc', 'KC', 'Kansas City'],
  ['laa', 'LAA', 'LA Angels'], ['lad', 'LAD', 'LA Dodgers'], ['mia', 'MIA', 'Miami'],
  ['mil', 'MIL', 'Milwaukee'], ['min', 'MIN', 'Minnesota'], ['nym', 'NYM', 'NY Mets'],
  ['nyy', 'NYY', 'New York'], ['ath', 'ATH', 'Athletics'], ['phi', 'PHI', 'Philadelphia'],
  ['pit', 'PIT', 'Pittsburgh'], ['sd', 'SD', 'San Diego'], ['sf', 'SF', 'San Fran.'],
  ['sea', 'SEA', 'Seattle'], ['stl', 'STL', 'St. Louis'], ['tb', 'TB', 'Tampa Bay'],
  ['tex', 'TEX', 'Texas'], ['tor', 'TOR', 'Toronto'], ['wsh', 'WSH', 'Washington'],
].map(([id, abbr, name]) => ({ id, abbr, name }));

export const playerFixtures: PlayerFixture[] = [
  { id: 1, name: 'Shohei Ohtani', team: 'LA Dodgers' },
  { id: 2, name: 'Aaron Judge', team: 'NY Yankees' },
  { id: 3, name: 'Mookie Betts', team: 'LA Dodgers' },
  { id: 4, name: 'Juan Soto', team: 'NY Mets' },
  { id: 5, name: 'Bryce Harper', team: 'Philadelphia' },
  { id: 6, name: 'Bobby Witt Jr.', team: 'Kansas City' },
  { id: 7, name: 'Gunnar Henderson', team: 'Baltimore' },
  { id: 8, name: 'Corbin Carroll', team: 'Arizona' },
];

export const initialFavoriteTeamIds = ['nyy', 'sd'];
export const initialFavoritePlayerIds = [1];

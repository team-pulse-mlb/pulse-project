import Header from './components/Header';
import SignupPage from './pages/SignupPage';


function App() {
  return (
    <div className='app'>
      <Header />
      <SignupPage />

      <main className='main-content'>
        <h2 className='title'>PULSE - App main</h2>
        <p>지금 볼 만한 MLB 경기를 찾아보세요.</p>
      </main>
    </div>
  );
}

export default App;